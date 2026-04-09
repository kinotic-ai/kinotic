package org.kinotic.core.api.secret;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Provides secure secret storage with scope-based isolation.
 * Each secret is identified by a (secretScope, key) pair. The underlying storage
 * uses HKDF-derived opaque names so the real scope and key are never stored.
 */
public interface SecretStorageService {

    CompletableFuture<Void> setSecret(String secretScope, String key, String value);

    CompletableFuture<String> getSecret(String secretScope, String key);

    CompletableFuture<Void> deleteSecret(String secretScope, String key);

    CompletableFuture<Void> setSecrets(String secretScope, Map<String, String> secrets);

    CompletableFuture<Map<String, String>> getSecrets(String secretScope, List<String> keys);

    CompletableFuture<Void> deleteSecrets(String secretScope, List<String> keys);
}
