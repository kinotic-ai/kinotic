package org.kinotic.management.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.AuthzModelService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.domain.api.utils.CursorPaging;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.Tasks;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.services.security.AuthzGraphSeedJob;
import org.kinotic.management.internal.api.services.ApplicationStoreProvisioner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.kinotic.authz.api.services.AuthzStoreService.SYSTEM;

/**
 * Places every organization and application stored without a place in the authorization graph. An organization
 * not yet on the system is placed with its enabled users as its members and its creator, while an enabled user of
 * it, as its administrator; an application without a store is provisioned one, so its users are answered by a
 * store of its own. A sign-up and an application's creation do the same for what they create, and this gives the
 * records a migration seeds the tuples those services would have written. An organization on the system and an
 * application with a store, whatever was granted or revoked since, are left as they are. A run that leaves an
 * organization or an application unplaced fails.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationGraphSeedJob implements AuthzGraphSeedJob {

    private static final int PAGE_SIZE = 100;

    private final AuthzModelService models;
    private final OrganizationService organizations;
    private final ParticipantIdentityService identities;
    private final ApplicationRepository applications;
    private final AuthzStoreRepository records;
    private final ApplicationStoreProvisioner provisioner;
    private final RelationshipService relationships;

    @Override
    public JobDefinition createJobDefinition() {
        return JobDefinition.create("Places the organizations and applications stored without a place in the authorization graph")
                            .name("seed-organization-graph")
                            .version("1")
                            .task(Tasks.fromCallable("Wait for the system model",
                                                     () -> models.awaitSystemModel(List.of(AuthzConstants.ORGANIZATION_ADMIN_ROLE))))
                            .task(Tasks.fromCallable("Place organizations and applications", this::placeAll));
    }

    private Future<Void> placeAll() {
        AtomicInteger placed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        return CursorPaging.forEachPage(organizations::findAll, PAGE_SIZE, Sort.by("id"), page -> {
            Future<Void> ret = Future.succeededFuture();
            for (Organization organization : page) {
                // one organization that fails leaves the others to be placed, and its applications
                ret = ret.compose(v -> counted(place(organization), placed, failed, "Organization " + organization.getId()))
                         .compose(v -> placeApplications(organization.getId(), placed, failed));
            }
            return ret;
        }).map(v -> {
            log.info("Placed {} organization(s) and application(s) in the authorization graph", placed.get());
            if (failed.get() > 0) {
                throw new IllegalStateException(failed.get() + " organization(s) and application(s) could not be placed in the authorization graph");
            }
            return null;
        });
    }

    private Future<Void> placeApplications(String organizationId, AtomicInteger placed, AtomicInteger failed) {
        return CursorPaging.forEachPage(pageable -> applications.findAll(organizationId, pageable), PAGE_SIZE, Sort.by("id"), page -> {
            Future<Void> ret = Future.succeededFuture();
            for (Application application : page) {
                ret = ret.compose(v -> counted(provision(application), placed, failed,
                                               "Application " + application.getId() + " of " + organizationId));
            }
            return ret;
        });
    }

    // Counts what was placed, and what failed so the run fails once the rest is placed
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

    // The containment is written last, so a run cut short leaves the organization for the next run to place again,
    // and the creator is bound under an id of its own, so a binding made twice is held once
    private Future<Boolean> placed(Organization organization, String object, RelationshipTuple containment) {
        Set<String> enabled = new HashSet<>();
        return members(organization.getId(), enabled)
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
    private Future<Void> members(String organizationId, Set<String> enabled) {
        return CursorPaging.forEachPage(pageable -> identities.findOrganizationUsers(organizationId, pageable), PAGE_SIZE, Sort.by("id"), page -> {
            List<RelationshipTuple> tuples = new ArrayList<>();
            for (OrganizationParticipantIdentity member : page) {
                if (member.isEnabled()) {
                    enabled.add(member.getId());
                    tuples.add(AuthzGraphUtil.organizationMember(member.getId(), organizationId));
                }
            }
            return relationships.ensure(SYSTEM, tuples);
        });
    }
}
