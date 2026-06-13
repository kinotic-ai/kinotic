package org.kinotic.core.internal.api.secret;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * SPI for secret storage backends. All methods operate on HKDF-derived opaque names,
 * never on raw scope/key values.
 */
public interface SecretStorageBackend {

    /**
     * Stores a secret value under the given HKDF-derived name.
     *
     * @param derivedName the opaque derived name (base64url, 43 chars)
     * @param value       the secret value to store
     * @return a future that completes when the secret has been persisted
     */
    CompletableFuture<Void> setSecret(String derivedName, String value);

    /**
     * Retrieves a secret value by its HKDF-derived name.
     *
     * @param derivedName the opaque derived name
     * @return a future containing the secret value, or {@code null} if not found
     */
    CompletableFuture<String> getSecret(String derivedName);

    /**
     * Deletes a secret by its HKDF-derived name.
     *
     * @param derivedName the opaque derived name
     * @return a future that completes when the secret has been deleted
     */
    CompletableFuture<Void> deleteSecret(String derivedName);

    /**
     * Stores multiple secrets in a single batch.
     *
     * @param derivedNameToValue a map of derived name to secret value
     * @return a future that completes when all secrets have been persisted
     */
    CompletableFuture<Void> setSecrets(Map<String, String> derivedNameToValue);

    /**
     * Retrieves multiple secrets in a single batch.
     *
     * @param derivedNames the list of derived names to look up
     * @return a future containing a map of derived name to secret value (missing keys are omitted)
     */
    CompletableFuture<Map<String, String>> getSecrets(List<String> derivedNames);

    /**
     * Deletes multiple secrets in a single batch.
     *
     * @param derivedNames the list of derived names to delete
     * @return a future that completes when all secrets have been deleted
     */
    CompletableFuture<Void> deleteSecrets(List<String> derivedNames);
}
