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
    private String filePath;
    private int maxEntries = 10000;
}
