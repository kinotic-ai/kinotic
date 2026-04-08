package org.kinotic.os.api.model.cluster;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Contains information about the Ignite cluster.
 */
@Data
@Builder
public class KinoticClusterInfo {

    /**
     * Indicates whether clustering is enabled.
     */
    @Builder.Default
    private boolean clusteringEnabled = false;
    
    /**
     * The unique identifier of the local node.
     */
    private String localNodeId;
    
    /**
     * The total number of server nodes in the cluster.
     */
    private int serverNodeCount;
    
    /**
     * The current topology version of the cluster.
     */
    private long topologyVersion;
    
    /**
     * The current state of the cluster (e.g., ACTIVE, INACTIVE).
     */
    private String clusterState;
    
    /**
     * Information about all server nodes in the cluster.
     */
    private List<KinoticNodeInfo> nodes;
    
    /**
     * Indicates whether the cluster is active.
     */
    private boolean active;
}
