package org.kinotic.structures.internal.api.services.impl.cluster;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.cluster.ClusterNode;
import org.kinotic.structures.api.domain.cluster.ClusterInfo;
import org.kinotic.structures.api.domain.cluster.NodeInfo;
import org.kinotic.structures.api.services.cluster.ClusterInfoService;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@AllArgsConstructor
public class DefaultClusterInfoService implements ClusterInfoService {

    // private final Ignite ignite;

    @Override
    public Mono<ClusterInfo> getClusterInfo() {
        Ignite ignite = Ignition.ignite();
        log.info("Getting cluster info for cluster: {}", ignite.cluster().id());
        // Get cluster group for all server nodes
        ClusterGroup servers = ignite.cluster().forServers();
        Collection<ClusterNode> serverNodes = servers.nodes();

        if(serverNodes.isEmpty()) {
            log.info("No server nodes found");
            return Mono.just(ClusterInfo.builder()
                    .localNodeId(ignite.cluster().localNode().id().toString())
                    .serverNodeCount(0)
                    .topologyVersion(ignite.cluster().topologyVersion())
                    .clusterState(ignite.cluster().state().name())
                    .active(false)
                    .nodes(Collections.emptyList())
                    .build());
        }else {
            log.info("Server nodes found: {}", serverNodes.size());
            // Get local node information
            ClusterNode localNode = ignite.cluster().localNode();
            
            // Map cluster nodes to NodeInfo objects
            List<NodeInfo> nodeInfos = serverNodes.stream()
                    .map(node -> mapToNodeInfo(node, localNode.id()))
                    .collect(Collectors.toList());
            
            // Build and return ClusterInfo
            ClusterInfo clusterInfo = ClusterInfo.builder()
                    .localNodeId(localNode.id().toString())
                    .serverNodeCount(serverNodes.size())
                    .topologyVersion(ignite.cluster().topologyVersion())
                    .clusterState(ignite.cluster().state().name())
                    .active(ignite.cluster().state().active())
                    .nodes(nodeInfos)
                    .build();
            
            return Mono.just(clusterInfo);
        }
    }
    
    /**
     * Maps an Ignite ClusterNode to a NodeInfo domain object.
     * 
     * @param node the cluster node to map
     * @param localNodeId the ID of the local node for comparison
     * @return the mapped NodeInfo object
     */
    private NodeInfo mapToNodeInfo(ClusterNode node, Object localNodeId) {
        // Convert attributes to a serializable map
        Map<String, Object> attributes = new HashMap<>();
        for (String key : node.attributes().keySet()) {
            Object value = node.attribute(key);
            // Only include simple, serializable attributes
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                attributes.put(key, value);
            }
        }
        
        return NodeInfo.builder()
                .nodeId(node.id().toString())
                .order(node.order())
                .local(node.id().equals(localNodeId))
                .addresses(node.addresses())
                .hostNames(node.hostNames())
                .client(node.isClient())
                .attributes(attributes)
                .version(node.version().toString())
                .build();
    }
}
