package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.domain.api.model.AuthzModelRevision;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.model.Requeue;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.services.Reconciler;
import org.springframework.stereotype.Component;


/**
 * The worker of the authorization stores: keeps the model a store runs equal to the one generated for it, the
 * platform's from the directory entries that belong to it and an application's from the platform's services
 * its users call and the services registered in its scope, and the store's built-in roles bundling what that
 * model has. The reconcile master calls it when a service of the store is registered, when the store is found
 * out of its desired state, and once when the master starts, so a model write the engine refused is made again
 * and a master lost mid-write is caught up. The model is written as a new version only when it differs from
 * the one the engine runs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthzStoreReconciler implements Reconciler<AuthzStore> {

    private final ServiceDirectory directory;
    private final AuthzModelGenerator generator;
    private final AuthzStoreService storeService;
    private final RelationshipService relationships;
    private final AuthzStoreRepository stores;

    @Override
    public WatchedType type() {
        return WatchedType.AUTHZ_STORE;
    }

    @Override
    public Future<Requeue> reconcile(AuthzStore current) {
        return modelOf(current).compose(model -> run(current, model)).map(Requeue.NONE);
    }

    // The platform's model from the platform's own services; an application's from the platform's services its
    // users call and whatever services its runtimes registered
    private Future<AuthzModel> modelOf(AuthzStore store) {
        Future<AuthzModel> ret;
        if (store.getApplicationId() == null) {
            ret = directory.findSystemDefinitions().map(generator::platformModel);
        } else {
            ret = directory.findSystemDefinitions()
                           .compose(platform -> directory.findApplicationDefinitions(store.getOrganizationId(), store.getApplicationId())
                                                         .map(services -> generator.applicationModel(platform, services)));
        }
        return ret;
    }

    // The directory's word is the record's intent, written here because only a read of it says what it is; the
    // engine's version follows, with the built-in roles the model implies brought in step ahead of it, so the
    // record is reconciled exactly when the engine runs the model the directory implies and its roles bundle what
    // that model has, and any of the writes failing leaves it for the master to retry
    private Future<Void> run(AuthzStore current, AuthzModel model) {
        AuthzModelRevision revision = new AuthzModelRevision(model.hash());
        String store = current.getId();
        return stores.updateDesired(store, revision, null, "service directory")
                     .compose(intended -> storeService.ensureStore(store)
                             .compose(id -> relationships.ensureModelWithRoles(store, model))
                             .compose(version -> stores.reportObserved(store, revision,
                                                                       intended.getState().getGeneration(),
                                                                       "engine version " + version)))
                     .onSuccess(v -> log.debug("Store '{}' reconciled to model {}", store, revision.hash()));
    }
}
