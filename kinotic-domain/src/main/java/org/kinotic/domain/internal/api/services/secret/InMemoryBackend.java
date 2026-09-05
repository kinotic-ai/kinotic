package org.kinotic.domain.internal.api.services.secret;

import io.vertx.core.Future;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory secret storage backend using {@link ConcurrentHashMap}.
 * Used as the default when no secret storage configuration is provided.
 */
public class InMemoryBackend implements SecretStorageBackend {

    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    @Override
    public Future<Void> setSecret(String derivedName, String value) {
        store.put(derivedName, value);
        return Future.succeededFuture();
    }

    @Override
    public Future<String> getSecret(String derivedName) {
        return Future.succeededFuture(store.get(derivedName));
    }

    @Override
    public Future<Void> deleteSecret(String derivedName) {
        store.remove(derivedName);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> setSecrets(Map<String, String> derivedNameToValue) {
        store.putAll(derivedNameToValue);
        return Future.succeededFuture();
    }

    @Override
    public Future<Map<String, String>> getSecrets(List<String> derivedNames) {
        Map<String, String> results = derivedNames.stream()
                                                  .filter(store::containsKey)
                                                  .collect(Collectors.toMap(k -> k, store::get));
        return Future.succeededFuture(results);
    }

    @Override
    public Future<Void> deleteSecrets(List<String> derivedNames) {
        derivedNames.forEach(store::remove);
        return Future.succeededFuture();
    }
}
