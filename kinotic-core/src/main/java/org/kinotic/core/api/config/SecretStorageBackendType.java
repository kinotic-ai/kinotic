package org.kinotic.core.api.config;

/**
 * Supported backend types for secret storage.
 */
public enum SecretStorageBackendType {
    /**
     * Chronicle Map (OpenHFT) backed secret storage that persists secrets to a memory-mapped file.
     * Intended for local development.
     */
    HFT,
    /**
     * Azure Key Vault backed secret storage.
     */
    AZURE
}
