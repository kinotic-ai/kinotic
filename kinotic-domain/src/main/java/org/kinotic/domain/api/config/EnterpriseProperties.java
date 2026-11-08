package org.kinotic.domain.api.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Configuration settings for the Kinotic Enterprise Features, under the {@code kinotic.ee} prefix.
 */
@Getter
@Setter
@Accessors(chain = true)
public class EnterpriseProperties {
    /**
     * A Kinotic Enterprise License key the server installs for the whole cluster as it starts, when no key is
     * installed or this one was issued later than the installed key.
     */
    private String licenseKey;
}
