package org.kinotic.system.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.stereotype.Component;

/**
 * Contributes the {@link SystemApiProperties} to the kinotic prefix.
 * Configuration is accessible via {@code kinotic.orchestrator.*}
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
public class KinoticSystemApiProperties extends KinoticProperties {

    /**
     * Orchestrator properties configuration.
     */
    private SystemApiProperties orchestrator = new SystemApiProperties();

    /**
     * When true the orchestrator module is not loaded: no grind execution, no run
     * recording, and no workload orchestration beans are registered.
     */
    private boolean disableSystemApi = false;

}
