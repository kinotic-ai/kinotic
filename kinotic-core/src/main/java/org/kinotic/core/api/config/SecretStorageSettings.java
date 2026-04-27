package org.kinotic.core.api.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Configuration settings for secret storage.
 * <p>
 * The master key set used for name derivation is no longer configured here — it is loaded
 * from the platform secrets mount (see {@link PlatformSecretsProperties}) so it can
 * rotate transparently without restarts.
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
     * Azure Key Vault settings. Required when {@code backend} is {@link SecretStorageBackendType#AZURE}.
     */
    private AzureSettings azure;
    /**
     * Chronicle Map settings. Required when {@code backend} is {@link SecretStorageBackendType#HFT}.
     */
    private ChronicleMapSettings chronicleMap;
}
