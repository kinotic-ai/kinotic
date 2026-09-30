package org.kinotic.managementserver.api.config;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Contributes the {@link ManagementServerProperties} to the kinotic prefix.
 * Configuration is accessible via {@code kinotic.managementServer.*}
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
@Validated
public class KinoticManagementServerProperties extends KinoticProperties {

    /**
     * The URLs the management server is reached at.
     */
    @Valid
    private ManagementServerProperties managementServer = new ManagementServerProperties();

}
