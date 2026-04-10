package org.kinotic.core.internal.secret;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory secret storage backend using {@link ConcurrentHashMap}.
 * Used as the default when no secret storage configuration is provided.
 */
public class InMemoryBackend implements SecretStorageBackend {

    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Void> setSecret(String derivedName, String value) {
        store.put(derivedName, value);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<String> getSecret(String derivedName) {
        return CompletableFuture.completedFuture(store.get(derivedName));
    }

    @Override
    public CompletableFuture<Void> deleteSecret(String derivedName) {
        store.remove(derivedName);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> setSecrets(Map<String, String> derivedNameToValue) {
        store.putAll(derivedNameToValue);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<String, String>> getSecrets(List<String> derivedNames) {
        Map<String, String> results = derivedNames.stream()
                                                  .filter(store::containsKey)
                                                  .collect(Collectors.toMap(k -> k, store::get));
        return CompletableFuture.completedFuture(results);
    }

    @Override
    public CompletableFuture<Void> deleteSecrets(List<String> derivedNames) {
        derivedNames.forEach(store::remove);
        return CompletableFuture.completedFuture(null);
    }
}
