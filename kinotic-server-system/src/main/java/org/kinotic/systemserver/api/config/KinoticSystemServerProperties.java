package org.kinotic.systemserver.api.config;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Contributes the {@link SystemServerProperties} to the kinotic prefix.
 * Configuration is accessible via {@code kinotic.systemServer.*}
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
@Validated
public class KinoticSystemServerProperties extends KinoticProperties {

    /**
     * The URLs the system server is reached at.
     */
    @Valid
    private SystemServerProperties systemServer = new SystemServerProperties();

}
