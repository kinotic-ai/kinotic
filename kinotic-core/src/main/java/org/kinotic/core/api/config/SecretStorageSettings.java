package org.kinotic.core.api.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Configuration settings for secret storage.
 */
@Getter
@Setter
@Accessors(chain = true)
public class SecretStorageSettings {
    /**
     * Backend type. If null, in-memory storage is used.
     */
    private SecretStorageBackendType backend;
    /**
     * Base64-encoded 32-byte HKDF master key for deriving opaque secret names.
     */
    private String masterKey;
    /**
     * Azure Key Vault settings. Required when {@code backend} is {@link SecretStorageBackendType#AZURE}.
     */
    private AzureSettings azure;
    /**
     * Chronicle Map settings. Required when {@code backend} is {@link SecretStorageBackendType#HFT}.
     */
    private ChronicleMapSettings chronicleMap;
}
