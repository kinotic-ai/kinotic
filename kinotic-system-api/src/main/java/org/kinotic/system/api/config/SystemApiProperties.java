package org.kinotic.system.api.config;

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
public class SystemApiProperties {

    /**
     * Node health monitoring configuration.
     */
    private VmNodeProperties vmNode = new VmNodeProperties();

    /**
     * Deployment of customer project workloads from GitHub pushes.
     */
    private DeploymentProperties deployment = new DeploymentProperties();

}
