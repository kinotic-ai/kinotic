package org.kinotic.management.internal.api.services.security;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.CursorPage;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import static org.kinotic.authz.api.services.AuthzStoreService.PLATFORM;

/**
 * Binds the creator of every organization that has no administrator as its administrator, once the server is
 * up and the platform store runs a model. A sign-up binds the creator of the organization it creates; an
 * organization a migration seeds has a record and a creator but no grant, and this gives it the same
 * administrator. An organization whose administrator has been revoked is left as it is.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationAdminBootstrap {

    private static final long RETRY_MILLIS = 2_000;
    private static final int PAGE_SIZE = 100;

    private final Vertx vertx;
    private final OrganizationService organizations;
    private final AuthzStoreService stores;
    private final RelationshipService relationships;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        vertx.runOnContext(v -> run());
    }

    private void run() {
        stores.platformModelId()
              .compose(modelId -> page(null, 0))
              .onSuccess(bound -> {
                  if (bound > 0) {
                      log.info("Bound the creator of {} organization(s) as administrator", bound);
                  }
              })
              // the reconciler writes the platform model shortly after the servers start, so a store that runs
              // none yet, like one that cannot be reached, is asked again
              .onFailure(e -> {
                  log.debug("The organization administrator sweep waits: {}", e.getMessage());
                  vertx.setTimer(RETRY_MILLIS, t -> run());
              });
    }

    private Future<Integer> page(String cursor, int bound) {
        return organizations.findAll(Pageable.create(cursor, PAGE_SIZE, Sort.by("id")))
                            .compose(page -> {
                                Future<Integer> ret = Future.succeededFuture(bound);
                                for (Organization organization : page.getContent()) {
                                    ret = ret.compose(count -> administer(organization).map(did -> did ? count + 1 : count));
                                }
                                String next = nextCursor(page);
                                return next == null ? ret : ret.compose(count -> page(next, count));
                            });
    }

    private static String nextCursor(Page<Organization> page) {
        return page instanceof CursorPage<Organization> cursorPage && !page.getContent().isEmpty() ? cursorPage.getCursor() : null;
    }

    // The creator is bound only when no binding on the organization grants the administrator role
    private Future<Boolean> administer(Organization organization) {
        Future<Boolean> ret;
        if (organization.getCreatedBy() == null) {
            ret = Future.succeededFuture(false);
        } else {
            String object = AuthzUtil.object(AuthzUtil.ORGANIZATION_TYPE, organization.getId());
            ret = administered(object).compose(administered -> administered
                    ? Future.succeededFuture(false)
                    : relationships.bind(PLATFORM,
                                         AuthzUtil.ORGANIZATION_ADMIN_ROLE,
                                         AuthzUtil.object(AuthzUtil.USER_TYPE, organization.getCreatedBy()),
                                         object)
                                   .map(bindingId -> true));
        }
        return ret;
    }

    private Future<Boolean> administered(String object) {
        return relationships.findGrants(PLATFORM, object)
                            .map(grants -> grants.stream().anyMatch(grant -> AuthzUtil.ORGANIZATION_ADMIN_ROLE.equals(grant.roleId())));
    }
}
