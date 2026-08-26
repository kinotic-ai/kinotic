package org.kinotic.system.api.config;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Contributes the {@link SystemApiProperties} to the kinotic prefix.
 * Configuration is accessible via {@code kinotic.systemApi.*}
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
@Validated
public class KinoticSystemApiProperties extends KinoticProperties {

    /**
     * System-api properties configuration.
     */
    @Valid
    private SystemApiProperties systemApi = new SystemApiProperties();

    /**
     * When true the orchestrator module is not loaded: no grind execution, no run
     * recording, and no workload orchestration beans are registered.
     */
    private boolean disableSystemApi = false;

}
