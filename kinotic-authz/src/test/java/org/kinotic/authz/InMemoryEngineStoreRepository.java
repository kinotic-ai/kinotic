package org.kinotic.authz;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.EngineStore;
import org.kinotic.authz.api.services.EngineStoreRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The recorded ids of the stores for the module's tests, which run without the server that keeps the stores'
 * records: a test records the ids of a store here, and every other store has its ids read from the engine.
 */
@Component
public class InMemoryEngineStoreRepository implements EngineStoreRepository {

    private final Map<String, EngineStore> recorded = new ConcurrentHashMap<>();

    public void save(String store, EngineStore engineStore) {
        recorded.put(store, engineStore);
    }

    @Override
    public Future<Map<String, EngineStore>> findEngineStores(Collection<String> stores) {
        Map<String, EngineStore> ret = new HashMap<>();
        for (String store : stores) {
            EngineStore engineStore = recorded.get(store);
            if (engineStore != null) {
                ret.put(store, engineStore);
            }
        }
        return Future.succeededFuture(ret);
    }
}
