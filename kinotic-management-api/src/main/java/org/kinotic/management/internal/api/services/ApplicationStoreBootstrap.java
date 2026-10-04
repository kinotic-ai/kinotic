package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.CursorPage;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.services.OrganizationService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Provisions the authorization store of every application that has none, once the server is up and the
 * engine answers. An application created by a service gets its store with its record; one a migration seeds,
 * or one created before stores were, gets it here, so its users are answered by a store of its own.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationStoreBootstrap {

    private static final long RETRY_MILLIS = 2_000;
    private static final int PAGE_SIZE = 100;

    private final Vertx vertx;
    private final OrganizationService organizations;
    private final ApplicationRepository applications;
    private final AuthzStoreRepository records;
    private final ApplicationStoreProvisioner provisioner;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        vertx.runOnContext(v -> run());
    }

    private void run() {
        organizationPage(null, 0)
                .onSuccess(provisioned -> {
                    if (provisioned > 0) {
                        log.info("Provisioned the authorization store of {} application(s)", provisioned);
                    }
                })
                // the engine may be unreachable while the stack starts; a sweep that fails is made again
                .onFailure(e -> {
                    log.debug("The application store sweep waits: {}", e.getMessage());
                    vertx.setTimer(RETRY_MILLIS, t -> run());
                });
    }

    private Future<Integer> organizationPage(String cursor, int provisioned) {
        return organizations.findAll(Pageable.create(cursor, PAGE_SIZE, Sort.by("id")))
                            .compose(page -> {
                                Future<Integer> ret = Future.succeededFuture(provisioned);
                                for (Organization organization : page.getContent()) {
                                    ret = ret.compose(count -> applicationPage(organization.getId(), 0, count));
                                }
                                String next = nextCursor(page);
                                return next == null ? ret : ret.compose(count -> organizationPage(next, count));
                            });
    }

    private Future<Integer> applicationPage(String organizationId, int pageNumber, int provisioned) {
        return applications.findAll(organizationId, Pageable.create(pageNumber, PAGE_SIZE, Sort.by("id")))
                           .compose(page -> {
                               Future<Integer> ret = Future.succeededFuture(provisioned);
                               for (Application application : page.getContent()) {
                                   ret = ret.compose(count -> records.findById(application.getId())
                                                                     .compose(record -> record != null
                                                                             ? Future.succeededFuture(count)
                                                                             : provisioner.provision(application).map(count + 1)));
                               }
                               return page.getContent().size() < PAGE_SIZE
                                       ? ret
                                       : ret.compose(count -> applicationPage(organizationId, pageNumber + 1, count));
                           });
    }

    private static String nextCursor(Page<Organization> page) {
        return page instanceof CursorPage<Organization> cursorPage && !page.getContent().isEmpty() ? cursorPage.getCursor() : null;
    }
}
