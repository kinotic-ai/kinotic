package org.kinotic.orchestrator.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Configuration properties for the orchestrator module.
 * Accessible via {@code kinotic.orchestrator.*}
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class OrchestratorProperties {

    /**
     * Node health monitoring configuration.
     */
    private VmNodeProperties vmNode = new VmNodeProperties();

}
