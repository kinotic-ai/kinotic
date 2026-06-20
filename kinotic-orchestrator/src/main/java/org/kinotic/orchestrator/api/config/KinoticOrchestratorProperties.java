package org.kinotic.orchestrator.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.stereotype.Component;

/**
 * Contributes the {@link OrchestratorProperties} to the kinotic prefix.
 * Configuration is accessible via {@code kinotic.orchestrator.*}
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Component
public class KinoticOrchestratorProperties extends KinoticProperties {

    /**
     * Orchestrator properties configuration.
     */
    private OrchestratorProperties orchestrator = new OrchestratorProperties();

}
