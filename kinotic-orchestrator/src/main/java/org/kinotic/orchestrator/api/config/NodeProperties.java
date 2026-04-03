package org.kinotic.orchestrator.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Configuration properties for VmNode health monitoring.
 * Accessible via {@code kinotic.orchestrator.node.*}
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class NodeProperties {

    /**
     * How long (in seconds) since the last heartbeat before a node is considered stale and marked OFFLINE.
     */
    private long heartbeatTimeoutSeconds = 90;

    /**
     * How often (in seconds) the health check runs to look for stale nodes.
     */
    private long healthCheckIntervalSeconds = 30;

}
