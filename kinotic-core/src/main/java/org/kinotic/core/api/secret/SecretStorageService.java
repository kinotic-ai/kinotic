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

    /**
     * Stores a secret value for the given scope and key.
     *
     * @param secretScope the isolation scope (e.g. tenant ID) used to derive the storage key
     * @param key         the logical key identifying the secret within the scope
     * @param value       the secret value to store
     * @return a future that completes when the secret has been persisted
     */
    CompletableFuture<Void> setSecret(String secretScope, String key, String value);

    /**
     * Retrieves a secret value for the given scope and key.
     *
     * @param secretScope the isolation scope used to derive the storage key
     * @param key         the logical key identifying the secret within the scope
     * @return a future containing the secret value, or {@code null} if not found
     */
    CompletableFuture<String> getSecret(String secretScope, String key);

    /**
     * Deletes a secret for the given scope and key.
     *
     * @param secretScope the isolation scope used to derive the storage key
     * @param key         the logical key identifying the secret within the scope
     * @return a future that completes when the secret has been deleted
     */
    CompletableFuture<Void> deleteSecret(String secretScope, String key);

    /**
     * Stores multiple secrets within a single scope.
     *
     * @param secretScope the isolation scope used to derive storage keys
     * @param secrets     a map of logical key to secret value
     * @return a future that completes when all secrets have been persisted
     */
    CompletableFuture<Void> setSecrets(String secretScope, Map<String, String> secrets);

    /**
     * Retrieves multiple secrets within a single scope.
     *
     * @param secretScope the isolation scope used to derive storage keys
     * @param keys        the logical keys identifying the secrets within the scope
     * @return a future containing a map of logical key to secret value (keys not found are omitted)
     */
    CompletableFuture<Map<String, String>> getSecrets(String secretScope, List<String> keys);

    /**
     * Deletes multiple secrets within a single scope.
     *
     * @param secretScope the isolation scope used to derive storage keys
     * @param keys        the logical keys identifying the secrets to delete
     * @return a future that completes when all secrets have been deleted
     */
    CompletableFuture<Void> deleteSecrets(String secretScope, List<String> keys);
}
