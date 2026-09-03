package org.kinotic.system.api.config;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Configuration properties for the system-api module.
 * Accessible via {@code kinotic.systemApi.*}
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
    @Valid
    private DeploymentProperties deployment = new DeploymentProperties();

}
