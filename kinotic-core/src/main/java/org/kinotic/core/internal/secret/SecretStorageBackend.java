package org.kinotic.core.internal.secret;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * SPI for secret storage backends. All methods operate on HKDF-derived opaque names,
 * never on raw scope/key values.
 */
public interface SecretStorageBackend {

    CompletableFuture<Void> setSecret(String derivedName, String value);

    CompletableFuture<String> getSecret(String derivedName);

    CompletableFuture<Void> deleteSecret(String derivedName);

    CompletableFuture<Void> setSecrets(Map<String, String> derivedNameToValue);

    CompletableFuture<Map<String, String>> getSecrets(List<String> derivedNames);

    CompletableFuture<Void> deleteSecrets(List<String> derivedNames);
}
