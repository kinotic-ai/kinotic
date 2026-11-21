package org.kinotic.domain.api.repositories;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.model.EngineStore;
import org.kinotic.authz.api.services.EngineStoreRepository;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.domain.api.model.AuthzModelRevision;
import org.kinotic.domain.api.model.AuthzStore;
import org.kinotic.domain.api.model.WatchEventType;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.api.utils.CursorPaging;
import org.kinotic.domain.internal.api.repositories.AbstractReconcilableRepository;
import org.kinotic.domain.internal.api.repositories.ReconcileStateRepository;
import org.kinotic.domain.internal.api.repositories.WatchedDocument;
import org.kinotic.domain.internal.api.repositories.WatchEventRepository;
import org.kinotic.domain.internal.api.repositories.WatchedChange;
import org.kinotic.domain.internal.api.repositories.WatchedIndex;
import org.kinotic.domain.internal.api.repositories.WatchedStateRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Elasticsearch repository for {@link AuthzStore}s over the {@code kinotic_authz_store} index, stored by id
 * alone: an application's record id carries its organization, and the system's store has none. Each record
 * names the engine's ids of its store, which the servers read here in place of asking the engine.
 */
@Component
public class AuthzStoreRepository extends AbstractReconcilableRepository<AuthzStore, AuthzModelRevision> implements EngineStoreRepository {
    private static final int PAGE_SIZE = 500;
    private static final WatchedIndex WATCHED = new WatchedIndex(WatchedType.AUTHZ_STORE, "kinotic_authz_store");
    // Ids equal to the recorded ones are a noop, so a pass of the worker that wrote no version leaves the record
    private static final String SAVE_ENGINE_STORE = ReconcileStateRepository.RECONCILE_FUNCTIONS + """
            if (ctx._source.engineStoreId == params.storeId && ctx._source.engineModelId == params.modelId) {
                ctx.op = 'noop';
            } else {
                ctx._source.engineStoreId = params.storeId;
                ctx._source.engineModelId = params.modelId;
                touched(ctx._source, reconcileState(ctx._source), params);
            }
            """;

    public AuthzStoreRepository(CrudServiceTemplate crudServiceTemplate,
                                WatchedStateRepository watchedStateRepository,
                                WatchEventRepository watchEventRepository,
                                ReconcileStateRepository reconcileStateRepository) {
        super(WATCHED, AuthzStore.class, crudServiceTemplate, watchedStateRepository, watchEventRepository, reconcileStateRepository);
    }

    @Override
    public String scopeOf(AuthzStore record) {
        return record.getOrganizationId();
    }

    /**
     * Records the engine's ids of a store with its record, and enters the change in the ledger. Ids equal to the
     * recorded ones leave the record as it is. Visible to search on completion.
     *
     * @param id          the store's record
     * @param engineStore the engine's id of the store and the id of the model version it runs
     * @param source      what caused it, for the ledger
     */
    public Future<Void> saveEngineStore(String id, EngineStore engineStore, String source) {
        Validate.notBlank(id, "id cannot be blank");
        Validate.notNull(engineStore, "engineStore cannot be null");
        return watchedStateRepository.write(document(id), SAVE_ENGINE_STORE,
                                            Map.of("storeId", engineStore.storeId(), "modelId", engineStore.modelId()),
                                            new WatchedChange(WatchEventType.STATUS_CHANGED, source,
                                                              "Engine store " + engineStore.storeId() + " runs model " + engineStore.modelId(),
                                                              engineStore))
                                     .mapEmpty();
    }

    @Override
    public Future<Map<String, EngineStore>> findEngineStores(Collection<String> stores) {
        return findAllById(stores, b -> b.sourceIncludes("id", "engineStoreId", "engineModelId")).map(records -> {
            Map<String, EngineStore> ret = new HashMap<>();
            for (AuthzStore record : records) {
                // the two ids are written together, so a record naming a store names its version
                if (record.getEngineStoreId() != null) {
                    ret.put(record.getId(), new EngineStore(record.getEngineStoreId(), record.getEngineModelId()));
                }
            }
            return ret;
        });
    }

    /**
     * @see ReconcileStateRepository#renewDesired(WatchedDocument, String)
     */
    public Future<Void> renewDesired(String id, String source) {
        return reconcileStateRepository.renewDesired(document(id), source).mapEmpty();
    }

    /**
     * Renews the intent of every store, the system's and each application's.
     *
     * @see ReconcileStateRepository#renewDesired(WatchedDocument, String)
     */
    public Future<Void> renewAllDesired(String source) {
        return CursorPaging.forEachPage(pageable -> findAll(pageable, b -> b.source(sc -> sc.filter(f -> f.includes("id")))),
                                        PAGE_SIZE,
                                        Sort.by("id"),
                                        page -> {
                                            Future<Void> ret = Future.succeededFuture();
                                            for (AuthzStore store : page) {
                                                ret = ret.compose(v -> renewDesired(store.getId(), source));
                                            }
                                            return ret;
                                        });
    }
}
