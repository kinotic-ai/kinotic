package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.AuthzModelRevision;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Gives an application its authorization store: the store in the engine, running the kernel model, the
 * platform's tenant services included, with its roles so the application's users and tenants can be placed in
 * it and granted on at once, and the store's record, which the reconciler keeps in step with the application's
 * entity definitions and registered services from then on. Provisioning is idempotent: an application that has its
 * store keeps it, and is placed in it if it is not.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationStoreProvisioner {

    private final AuthzStoreService stores;
    private final AuthzModelGenerator generator;
    private final RelationshipService relationships;
    private final AuthzStoreRepository records;
    private final ServiceDirectory directory;

    public Future<Void> provision(Application application) {
        return directory.findSystemDefinitions()
                     .map(platform -> generator.applicationModel(platform, List.of()))
                     .compose(kernel -> provision(application, kernel));
    }

    private Future<Void> provision(Application application, AuthzModel kernel) {
        String store = DomainUtil.authzApplicationId(application.getOrganizationId(), application.getId());
        return stores.ensureStore(store)
                     // the kernel model and its roles admit the membership tuples written at user creation and the
                     // grants made before any service of the application registers; a store already running a
                     // model keeps it, since the reconciler's carries the application's services
                     .compose(id -> stores.modelId(store).recover(none -> relationships.ensureModelWithRoles(store, kernel)))
                     // the placement is ensured whatever model the store runs: a reconcile that ran while an
                     // application of the same id was deleted creates the store again with a model and nothing else
                     .compose(version -> relationships.ensure(store, List.of(placement(store))))
                     .compose(v -> records.findById(store))
                     .compose(record -> {
                         Future<Void> ret;
                         if (record != null) {
                             ret = Future.succeededFuture();
                         } else {
                             AuthzStore created = new AuthzStore().setId(store)
                                                                  .setOrganizationId(application.getOrganizationId())
                                                                  .setApplicationId(application.getId());
                             // created with the kernel model as its intent, so a service registered before the
                             // worker's first run stamps a record that exists, and the master reconciles the
                             // record to the model the services imply on its next look
                             ret = records.updateDesired(store, new AuthzModelRevision(kernel.hash()), created, "application created")
                                          .onSuccess(v -> log.info("Provisioned the authorization store of application {}", store))
                                          .mapEmpty();
                         }
                         return ret;
                     });
    }

    // The one tuple the application holds in its own store: everyone is placed on it, which the placement of each
    // definition under it is read through
    private static RelationshipTuple placement(String store) {
        return new RelationshipTuple(AuthzUtil.EVERYONE, AuthzUtil.PLACED_RELATION, AuthzUtil.object(AuthzUtil.APPLICATION_TYPE, store));
    }
}
