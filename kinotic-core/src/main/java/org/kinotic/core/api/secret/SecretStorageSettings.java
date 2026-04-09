package org.kinotic.core.api.secret;

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
     * Backend type: "azure" or "chronicle-map". If null, in-memory storage is used.
     */
    private String backend;
    /**
     * Base64-encoded 32-byte HKDF master key for deriving opaque secret names.
     */
    private String masterKey;
    private AzureSettings azure;
    private ChronicleMapSettings chronicleMap;
}
