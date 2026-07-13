package org.kinotic.domain.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.config.KinoticProperties;

/**
 * Contributes domain-level configuration (e.g. {@link EmailProperties}) to the
 * {@code kinotic} prefix. Registered via {@code @EnableConfigurationProperties} on the
 * always-on auto-configuration (not the gated domain library), so it stays available — the
 * api-gateway reads {@link DomainProperties#getSsl()} even when domain logic is disabled.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class KinoticDomainProperties extends KinoticProperties {

    /**
     * If true, domain functionality will not be loaded.
     */
    private boolean disableDomain = false;

    private DomainProperties domain = new DomainProperties();

}
