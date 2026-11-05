package org.kinotic.domain.api.config;

/**
 * Supported backend types for secret storage.
 */
public enum SecretStorageBackendType {
    /**
     * Process-memory secret storage; every stored secret is lost when the process stops.
     */
    IN_MEMORY,
    /**
     * Azure Key Vault backed secret storage, an Enterprise Feature.
     */
    AZURE
}
