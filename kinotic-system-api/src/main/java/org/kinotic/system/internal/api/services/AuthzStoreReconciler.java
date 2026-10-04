package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.authz.api.model.EntityResource;
import org.kinotic.authz.api.model.EntityScope;
import org.kinotic.authz.api.services.AuthzModelGenerator;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.domain.api.model.AuthzModelRevision;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.model.Requeue;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
import org.kinotic.domain.api.model.persistence.idl.decorators.MultiTenancyType;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.repositories.EntityDefinitionRepository;
import org.kinotic.domain.api.services.Reconciler;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The worker of the authorization stores: keeps the model a store runs equal to the one generated for it, the
 * platform's from the directory entries that belong to it and an application's from its published entity
 * definitions and the contracts registered in its scope, and the store's built-in roles bundling what that
 * model has. The reconcile master calls it when a contract of the store is published, when an application's
 * definitions change, when the store is found out of its desired state, and once when the master starts, so a
 * model write the engine refused is made again and a master lost mid-write is caught up. The model is written
 * as a new version only when it differs from the one the engine runs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthzStoreReconciler implements Reconciler<AuthzStore> {

    // An application's published definitions are read in one page: an application has tens, not thousands
    private static final int DEFINITION_PAGE_SIZE = 1000;

    private final ServiceDirectory directory;
    private final AuthzModelGenerator generator;
    private final AuthzStoreService storeService;
    private final RelationshipService relationships;
    private final AuthzStoreRepository stores;
    private final EntityDefinitionRepository entityDefinitions;

    @Override
    public WatchedType type() {
        return WatchedType.AUTHZ_STORE;
    }

    @Override
    public Future<Requeue> reconcile(AuthzStore current) {
        return modelOf(current).compose(model -> run(current, model)).map(Requeue.NONE);
    }

    // The platform's model from the platform's own contracts; an application's from its published definitions
    // and whatever contracts its own services registered
    private Future<AuthzModel> modelOf(AuthzStore store) {
        Future<AuthzModel> ret;
        if (store.getApplicationId() == null) {
            ret = directory.findSystemContracts().map(generator::platformModel);
        } else {
            ret = directory.findApplicationContracts(store.getOrganizationId(), store.getApplicationId())
                           .compose(contracts -> entityDefinitions.findAllPublishedForApplication(store.getApplicationId(),
                                                                                                  store.getOrganizationId(),
                                                                                                  Pageable.create(0, DEFINITION_PAGE_SIZE, Sort.by("id")))
                                                                  .map(page -> {
                                                                      List<EntityResource> entities = new ArrayList<>();
                                                                      for (EntityDefinition definition : page.getContent()) {
                                                                          entities.add(entityOf(definition));
                                                                      }
                                                                      return generator.applicationModel(contracts, entities);
                                                                  }));
        }
        return ret;
    }

    // A definition's rows are typed by its name, in the tenant for a shared definition and in the application otherwise
    private static EntityResource entityOf(EntityDefinition definition) {
        return new EntityResource(DomainUtil.entityTypeOf(definition.getId()),
                                  definition.getMultiTenancyType() == MultiTenancyType.SHARED ? EntityScope.TENANT : EntityScope.APPLICATION);
    }

    // The directory's and the definitions' word is the record's intent, written here because only a read of
    // them says what it is; the engine's version follows, with the built-in roles the model implies brought in
    // step behind it, so the record is reconciled exactly when the engine runs the model they imply and its roles
    // bundle what that model has, and any of the writes failing leaves it for the master to retry
    private Future<Void> run(AuthzStore current, AuthzModel model) {
        AuthzModelRevision revision = new AuthzModelRevision(model.hash());
        String store = current.getId();
        return stores.updateDesired(store, revision, null, "service directory")
                     .compose(intended -> storeService.ensureStore(store)
                             .compose(id -> storeService.ensureModel(store, model))
                             .compose(version -> relationships.ensureRoles(store, model.roles()).map(version))
                             .compose(version -> stores.reportObserved(store, revision,
                                                                       intended.getState().getGeneration(),
                                                                       "engine version " + version)))
                     .onSuccess(v -> log.debug("Store '{}' reconciled to model {}", store, revision.hash()));
    }
}
