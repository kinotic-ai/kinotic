package org.kinotic.structures.api.domain.cluster;

import java.util.Collection;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contains information about a single node in the Ignite cluster.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeInfo {
    
    /**
     * The unique identifier of the node.
     */
    private String nodeId;
    
    /**
     * The order in which the node joined the cluster.
     */
    private long order;
    
    /**
     * Indicates whether this is the local node.
     */
    private boolean local;
    
    /**
     * The collection of IP addresses for this node.
     */
    private Collection<String> addresses;
    
    /**
     * The collection of host names for this node.
     */
    private Collection<String> hostNames;
    
    /**
     * Indicates whether this is a client node.
     */
    private boolean client;
    
    /**
     * Custom attributes associated with this node.
     */
    private Map<String, Object> attributes;
    
    /**
     * The version of Ignite running on this node.
     */
    private String version;
}
