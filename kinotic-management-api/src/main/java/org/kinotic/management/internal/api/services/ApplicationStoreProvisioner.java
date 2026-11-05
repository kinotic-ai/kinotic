package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.CursorPage;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.AuthzModelRevision;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.model.Tenant;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.repositories.EntityDefinitionRepository;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.management.api.repositories.ProjectRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.kinotic.authz.api.services.AuthzStoreService.SYSTEM;

/**
 * Places an application in the authorization graph: under its organization in the system's store, and in a
 * store of its own, running the kernel model, the system's tenant services included, with its roles so the
 * application's users and tenants can be placed in it and granted on at once, and the store's record, which the
 * reconciler keeps in step with the application's entity definitions and registered services from then on.
 * Whatever the application already holds, its projects, entity definitions and users, is placed with it.
 * Provisioning is idempotent: an application that has its store keeps it, and is placed in it if it is not.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationStoreProvisioner {

    private static final int PAGE_SIZE = 500;

    private final AuthzStoreService stores;
    private final AuthzModelGenerator generator;
    private final RelationshipService relationships;
    private final AuthzStoreRepository records;
    private final ServiceDirectory directory;
    private final ProjectRepository projects;
    private final EntityDefinitionRepository entityDefinitions;
    private final ParticipantIdentityService identities;

    /**
     * Places the application in the graph and gives it its store.
     *
     * @param application the application, as stored
     * @return the application
     */
    public Future<Application> provision(Application application) {
        String organizationId = application.getOrganizationId();
        return relationships.ensure(SYSTEM, List.of(containment(organizationId, application.getId())))
                            .compose(v -> directory.findSystemDefinitions())
                            .map(system -> generator.applicationModel(system, List.of()))
                            .compose(kernel -> provision(application, kernel))
                            .compose(v -> placeHoldings(application))
                            .map(application);
    }

    /**
     * Takes a tenant out of its application's store: every grant made on it or on a definition within it, the
     * memberships of its users and its place under the application, so a tenant created again under its id starts
     * with none of them.
     *
     * @param tenant the tenant, as stored
     * @return a future completing once the store holds nothing of the tenant
     */
    public Future<Void> removeTenant(Tenant tenant) {
        String store = AuthzGraphUtil.authzApplicationId(tenant.getOrganizationId(), tenant.getApplicationId());
        String tenantObject = AuthzUtil.object(AuthzConstants.TENANT_TYPE, tenant.getId());
        return eachPage(pageable -> entityDefinitions.findAllForApplication(tenant.getApplicationId(), tenant.getOrganizationId(), pageable),
                        page -> {
                            Future<Void> ret = Future.succeededFuture();
                            for (EntityDefinition definition : page) {
                                String within = AuthzUtil.object(AuthzConstants.TENANT_DEFINITION_TYPE,
                                                                 AuthzUtil.tenantDefinitionId(definition.getId(), tenant.getId()));
                                ret = ret.compose(v -> relationships.removeObject(store, within));
                            }
                            return ret;
                        })
                .compose(v -> relationships.removeObject(store, tenantObject));
    }

    /**
     * The tuple placing an application under its organization in the system's store.
     *
     * @param organizationId the application's organization
     * @param applicationId  the application
     * @return the containment tuple
     */
    public static RelationshipTuple containment(String organizationId, String applicationId) {
        return AuthzGraphUtil.containment(AuthzUtil.object(AuthzConstants.ORGANIZATION_TYPE, organizationId),
                                          AuthzGraphUtil.authzApplication(organizationId, applicationId));
    }

    private Future<Void> provision(Application application, AuthzModel kernel) {
        String store = AuthzGraphUtil.authzApplicationId(application.getOrganizationId(), application.getId());
        return stores.ensureStore(store)
                     .compose(id -> records.findById(store))
                     .compose(record -> {
                         Future<Void> ret;
                         if (record != null) {
                             // a store with a record runs the reconciler's model, which carries the application's
                             // services, so only the placement is ensured
                             ret = relationships.ensure(store, List.of(placement(store)));
                         } else {
                             AuthzStore created = new AuthzStore().setId(store)
                                                                  .setOrganizationId(application.getOrganizationId())
                                                                  .setApplicationId(application.getId());
                             // the kernel model and its roles admit the membership tuples written at user creation
                             // and the grants made before any service of the application registers. It is written
                             // over whatever model the store runs: a reconcile that ran while an application of the
                             // same id was deleted creates the store again, and may leave it running the bridge
                             // version ensureModelWithRoles writes first, which has no application type.
                             // The record is created with the kernel model as its intent, so a service registered
                             // before the worker's first run stamps a record that exists, and the master reconciles
                             // the record to the model the services imply on its next look
                             ret = relationships.ensureModelWithRoles(store, kernel)
                                                .compose(version -> relationships.ensure(store, List.of(placement(store))))
                                                .compose(v -> records.updateDesired(store, new AuthzModelRevision(kernel.hash()), created, "application created"))
                                                .onSuccess(v -> log.info("Provisioned the authorization store of application {}", store))
                                                .mapEmpty();
                         }
                         return ret;
                     });
    }

    // An application a migration seeds holds projects, definitions and users that were never placed, since no
    // service created them; one created by the service holds none yet
    private Future<Void> placeHoldings(Application application) {
        String organizationId = application.getOrganizationId();
        String applicationId = application.getId();
        String store = AuthzGraphUtil.authzApplicationId(organizationId, applicationId);
        return eachPage(pageable -> projects.findAllForApplication(applicationId, organizationId, pageable),
                        page -> relationships.ensure(SYSTEM, page.stream().map(DefaultProjectService::containment).toList()))
                .compose(v -> eachPage(pageable -> entityDefinitions.findAllForApplication(applicationId, organizationId, pageable),
                                       page -> {
                                           List<RelationshipTuple> placed = page.stream().map(DefaultEntityDefinitionService::containment).toList();
                                           return relationships.ensure(SYSTEM, placed).compose(w -> relationships.ensure(store, placed));
                                       }))
                .compose(v -> eachPage(pageable -> identities.findApplicationUsers(organizationId, applicationId, pageable),
                                       page -> {
                                           List<RelationshipTuple> endUsers = new ArrayList<>();
                                           List<RelationshipTuple> inApplication = new ArrayList<>();
                                           page.forEach(user -> {
                                               List<RelationshipTuple> tuples = AuthzGraphUtil.applicationUserTuples(user);
                                               endUsers.add(tuples.getFirst());
                                               inApplication.addAll(tuples);
                                           });
                                           return relationships.ensure(SYSTEM, endUsers).compose(w -> relationships.ensure(store, inApplication));
                                       }));
    }

    private static <T> Future<Void> eachPage(Function<Pageable, Future<Page<T>>> pager, Function<List<T>, Future<Void>> action) {
        return eachPage(pager, action, null);
    }

    // Pages by cursor in id order, so every document is read once however many there are, past the offset window
    private static <T> Future<Void> eachPage(Function<Pageable, Future<Page<T>>> pager, Function<List<T>, Future<Void>> action, String cursor) {
        return pager.apply(Pageable.create(cursor, PAGE_SIZE, Sort.by("id"))).compose(page -> {
            Future<Void> ret;
            if (page.getContent().isEmpty()) {
                ret = Future.succeededFuture();
            } else {
                ret = action.apply(page.getContent());
                if (page.getContent().size() == PAGE_SIZE && page instanceof CursorPage<T> cursorPage) {
                    ret = ret.compose(v -> eachPage(pager, action, cursorPage.getCursor()));
                }
            }
            return ret;
        });
    }

    // The one tuple the application holds in its own store: everyone is placed on it, which the placement of each
    // definition under it is read through
    private static RelationshipTuple placement(String store) {
        return new RelationshipTuple(AuthzConstants.EVERYONE, AuthzConstants.PLACED_RELATION, AuthzUtil.object(AuthzConstants.APPLICATION_TYPE, store));
    }
}
