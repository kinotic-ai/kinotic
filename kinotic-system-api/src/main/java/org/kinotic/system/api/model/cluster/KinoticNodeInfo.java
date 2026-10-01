package org.kinotic.system.api.model.cluster;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;

/**
 * A single server node in the cluster: an org, system or app server.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KinoticNodeInfo {
    
    /**
     * The unique identifier of the node.
     */
    private String nodeId;
    
    /**
     * The order in which the node joined the cluster.
     */
    private long order;
    
    /**
     * The server kind the node runs, such as {@code management}, {@code system} or {@code app}.
     */
    private String serverName;

    /**
     * The collection of IP addresses for this node.
     */
    private Collection<String> addresses;
    
    /**
     * The collection of host names for this node.
     */
    private Collection<String> hostNames;
    
    /**
     * The Kinotic version the node runs; null when the node runs from classes rather than a packaged jar.
     */
    private String version;

    /**
     * The service name that labels the logs of the server the node runs, which selects them in a server log query;
     * null when the deployment configures none.
     */
    private String telemetryServiceName;

    /**
     * The service instance id that labels the node's own logs, which narrows a server log query to this node; null
     * when the deployment configures none.
     */
    private String telemetryServiceInstanceId;
}
