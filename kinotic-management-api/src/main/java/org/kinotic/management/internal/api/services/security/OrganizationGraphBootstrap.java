package org.kinotic.management.internal.api.services.security;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.CursorPage;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.internal.api.services.ApplicationStoreProvisioner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.kinotic.authz.api.services.AuthzStoreService.SYSTEM;

/**
 * Places every organization and application a migration seeded in the authorization graph, once the server is up
 * and the system store runs a model. An organization not yet on the system is placed with its enabled users as
 * its members and its creator, while an enabled user of it, as its administrator; an application
 * without a store is provisioned one, so its users are answered by a store of its own. A sign-up and an
 * application's creation do the same for what they create; a migration seeds records but no tuples, and this gives
 * them the ones those services would have. An organization on the system and an application with a store, whatever
 * was granted or revoked since, are left as they are.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationGraphBootstrap {

    private static final long RETRY_MILLIS = 2_000;
    // an organization or application that could not be placed is tried again by a later sweep, after a delay that
    // doubles with each sweep that leaves one, since a model the system store runs at startup may lack the types the
    // sweep writes
    private static final long MAX_RETRY_MILLIS = 60_000;
    private static final int PAGE_SIZE = 100;

    private final Vertx vertx;
    private final OrganizationService organizations;
    private final ParticipantIdentityService identities;
    private final ApplicationRepository applications;
    private final AuthzStoreRepository records;
    private final ApplicationStoreProvisioner provisioner;
    private final AuthzStoreService stores;
    private final RelationshipService relationships;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        vertx.runOnContext(v -> run(RETRY_MILLIS));
    }

    private void run(long failedDelay) {
        AtomicInteger placed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        stores.modelId(SYSTEM)
              .compose(modelId -> organizationPage(null, placed, failed))
              .onSuccess(v -> {
                  if (placed.get() > 0) {
                      log.info("Placed {} organization(s) and application(s) in the authorization graph", placed.get());
                  }
                  if (failed.get() > 0) {
                      log.warn("{} organization(s) and application(s) could not be placed in the authorization graph; the sweep is made again",
                               failed.get());
                      vertx.setTimer(failedDelay, t -> run(Math.min(failedDelay * 2, MAX_RETRY_MILLIS)));
                  }
              })
              // the reconciler writes the system model shortly after the servers start, so a store that runs
              // none yet, like one that cannot be reached, is asked again
              .onFailure(e -> {
                  log.debug("The organization graph sweep waits: {}", e.getMessage());
                  vertx.setTimer(RETRY_MILLIS, t -> run(failedDelay));
              });
    }

    private Future<Void> organizationPage(String cursor, AtomicInteger placed, AtomicInteger failed) {
        return organizations.findAll(Pageable.create(cursor, PAGE_SIZE, Sort.by("id")))
                            .compose(page -> {
                                Future<Void> ret = Future.succeededFuture();
                                for (Organization organization : page.getContent()) {
                                    // one organization that fails leaves the others to be placed, and its applications
                                    ret = ret.compose(v -> counted(place(organization), placed, failed,
                                                                   "Organization " + organization.getId()))
                                             .compose(v -> applicationPage(organization.getId(), 0, placed, failed));
                                }
                                String next = nextCursor(page);
                                return next == null ? ret : ret.compose(v -> organizationPage(next, placed, failed));
                            });
    }

    private Future<Void> applicationPage(String organizationId, int pageNumber, AtomicInteger placed, AtomicInteger failed) {
        return applications.findAll(organizationId, Pageable.create(pageNumber, PAGE_SIZE, Sort.by("id")))
                           .compose(page -> {
                               Future<Void> ret = Future.succeededFuture();
                               for (Application application : page.getContent()) {
                                   ret = ret.compose(v -> counted(provision(application), placed, failed,
                                                                  "Application " + application.getId() + " of " + organizationId));
                               }
                               return page.getContent().size() < PAGE_SIZE
                                       ? ret
                                       : ret.compose(v -> applicationPage(organizationId, pageNumber + 1, placed, failed));
                           });
    }

    // Counts what was placed, and what failed so the sweep is made again, leaving the rest of the sweep to run
    private static Future<Void> counted(Future<Boolean> placement, AtomicInteger placed, AtomicInteger failed, String what) {
        return placement.map(did -> {
                            if (did) {
                                placed.incrementAndGet();
                            }
                            return (Void) null;
                        })
                        .recover(e -> {
                            log.warn("{} could not be placed in the authorization graph: {}", what, e.getMessage());
                            failed.incrementAndGet();
                            return Future.succeededFuture();
                        });
    }

    private static String nextCursor(Page<Organization> page) {
        return page instanceof CursorPage<Organization> cursorPage && !page.getContent().isEmpty() ? cursorPage.getCursor() : null;
    }

    private Future<Boolean> provision(Application application) {
        return records.findById(AuthzGraphUtil.authzApplicationId(application.getOrganizationId(), application.getId()))
                      .compose(record -> record != null
                              ? Future.succeededFuture(false)
                              : provisioner.provision(application).map(true));
    }

    private Future<Boolean> place(Organization organization) {
        String object = AuthzUtil.object(AuthzConstants.ORGANIZATION_TYPE, organization.getId());
        RelationshipTuple containment = AuthzGraphUtil.containment(AuthzConstants.SYSTEM_OBJECT, object);
        return relationships.holds(SYSTEM, containment)
                            .compose(held -> held ? Future.succeededFuture(false) : placed(organization, object, containment));
    }

    // The containment is written last, so a sweep cut short places the organization again, and the creator is
    // bound under an id of its own, so every server sweeping at once binds it once
    private Future<Boolean> placed(Organization organization, String object, RelationshipTuple containment) {
        Set<String> enabled = new HashSet<>();
        return members(organization.getId(), 0, enabled)
                .compose(v -> {
                    Future<Void> ret = Future.succeededFuture();
                    // the creator administers the organization while it is an enabled member of it
                    if (organization.getCreatedBy() != null && enabled.contains(organization.getCreatedBy())) {
                        String creator = AuthzGraphUtil.authzUser(organization.getCreatedBy());
                        ret = relationships.ensureBound(SYSTEM,
                                                        AuthzUtil.roleBindingIdOf(AuthzConstants.ORGANIZATION_ADMIN_ROLE, AuthzUtil.idOf(creator)),
                                                        AuthzConstants.ORGANIZATION_ADMIN_ROLE,
                                                        creator,
                                                        object);
                    }
                    return ret;
                })
                .compose(v -> relationships.ensure(SYSTEM, List.of(containment)))
                .map(true);
    }

    // Makes the organization's enabled users its members, collecting their ids
    private Future<Void> members(String organizationId, int pageNumber, Set<String> enabled) {
        return identities.findOrganizationUsers(organizationId, Pageable.create(pageNumber, PAGE_SIZE, Sort.by("id"))).compose(page -> {
            List<RelationshipTuple> tuples = new ArrayList<>();
            for (OrganizationParticipantIdentity member : page.getContent()) {
                if (member.isEnabled()) {
                    enabled.add(member.getId());
                    tuples.add(AuthzGraphUtil.organizationMember(member.getId(), organizationId));
                }
            }
            Future<Void> ret = relationships.ensure(SYSTEM, tuples);
            return page.getContent().size() < PAGE_SIZE ? ret : ret.compose(v -> members(organizationId, pageNumber + 1, enabled));
        });
    }
}
