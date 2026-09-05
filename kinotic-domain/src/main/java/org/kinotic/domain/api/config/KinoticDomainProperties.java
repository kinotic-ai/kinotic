package org.kinotic.domain.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import jakarta.validation.Valid;
import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Contributes domain-level configuration (e.g. {@link EmailProperties}) under the
 * {@code kinotic.domain} prefix.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
@Validated
public class KinoticDomainProperties extends KinoticProperties {

    /**
     * If true, domain functionality will not be loaded.
     */
    private boolean disableDomain = false;

    @Valid
    private DomainProperties domain = new DomainProperties();

}
