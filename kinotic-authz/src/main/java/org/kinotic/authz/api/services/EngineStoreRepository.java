package org.kinotic.authz.api.services;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.EngineStore;

import java.util.Collection;
import java.util.Map;

/**
 * The engine's ids of the stores as they are recorded with each store's record, so a server names a store and
 * its model version in every call without asking the engine for them. The server that keeps the records
 * implements it; a store with no recorded ids has them read from the engine.
 */
public interface EngineStoreRepository {

    /**
     * The recorded ids of the given stores.
     *
     * @param stores the stores, each named as its record is
     * @return the ids by store, holding the stores whose records name both a store and a model version
     */
    Future<Map<String, EngineStore>> findEngineStores(Collection<String> stores);
}
