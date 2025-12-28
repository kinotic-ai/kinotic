package org.kinotic.structures.api.domain.cluster;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contains information about the Ignite cluster.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterInfo {
    
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
    private List<NodeInfo> nodes;
    
    /**
     * Indicates whether the cluster is active.
     */
    private boolean active;
}
