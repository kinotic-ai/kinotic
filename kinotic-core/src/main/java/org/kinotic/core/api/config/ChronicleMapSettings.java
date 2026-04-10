package org.kinotic.core.api.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Chronicle Map backend configuration settings.
 */
@Getter
@Setter
@Accessors(chain = true)
public class ChronicleMapSettings {
    /**
     * Path to the Chronicle Map file on disk where secrets are persisted.
     */
    private String filePath;
    /**
     * Maximum number of entries the Chronicle Map can hold. Defaults to {@code 10000}.
     */
    private int maxEntries = 10000;
}
