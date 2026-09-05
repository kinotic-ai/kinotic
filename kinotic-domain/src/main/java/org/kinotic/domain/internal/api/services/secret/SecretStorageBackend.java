package org.kinotic.domain.internal.api.services.secret;

import io.vertx.core.Future;

import java.util.List;
import java.util.Map;

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
    Future<Void> setSecret(String derivedName, String value);

    /**
     * Retrieves a secret value by its HKDF-derived name.
     *
     * @param derivedName the opaque derived name
     * @return a future containing the secret value, or {@code null} if not found
     */
    Future<String> getSecret(String derivedName);

    /**
     * Deletes a secret by its HKDF-derived name.
     *
     * @param derivedName the opaque derived name
     * @return a future that completes when the secret has been deleted
     */
    Future<Void> deleteSecret(String derivedName);

    /**
     * Stores multiple secrets in a single batch.
     *
     * @param derivedNameToValue a map of derived name to secret value
     * @return a future that completes when all secrets have been persisted
     */
    Future<Void> setSecrets(Map<String, String> derivedNameToValue);

    /**
     * Retrieves multiple secrets in a single batch.
     *
     * @param derivedNames the list of derived names to look up
     * @return a future containing a map of derived name to secret value (missing keys are omitted)
     */
    Future<Map<String, String>> getSecrets(List<String> derivedNames);

    /**
     * Deletes multiple secrets in a single batch.
     *
     * @param derivedNames the list of derived names to delete
     * @return a future that completes when all secrets have been deleted
     */
    Future<Void> deleteSecrets(List<String> derivedNames);
}
