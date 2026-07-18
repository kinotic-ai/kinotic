package org.kinotic.domain.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.stereotype.Component;

/**
 * Contributes domain-level configuration (e.g. {@link EmailProperties}) under the
 * {@code kinotic.domain} prefix.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
public class KinoticDomainProperties extends KinoticProperties {

    /**
     * If true, domain functionality will not be loaded.
     */
    private boolean disableDomain = false;

    private DomainProperties domain = new DomainProperties();

}
