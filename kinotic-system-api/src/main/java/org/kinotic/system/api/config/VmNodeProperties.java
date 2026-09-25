package org.kinotic.system.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Configuration properties for VmNode health monitoring.
 * Accessible via {@code kinotic.systemApi.vmNode.*}
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class VmNodeProperties {

    /**
     * How long (in seconds) since the last heartbeat before a node is considered silent and marked
     * unreachable with every run still open on it.
     */
    private long heartbeatTimeoutSeconds = 90;

}
