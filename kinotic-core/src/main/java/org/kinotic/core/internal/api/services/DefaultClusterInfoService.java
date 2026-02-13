package org.kinotic.core.internal.api.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.cluster.ClusterNode;
import org.kinotic.core.api.domain.cluster.ClusterInfo;
import org.kinotic.core.api.domain.cluster.ClusterInfo.ClusterInfoBuilder;
import org.kinotic.core.api.domain.cluster.NodeInfo;
import org.kinotic.core.api.services.ClusterInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DefaultClusterInfoService implements ClusterInfoService {

    private final Ignite ignite;

    public DefaultClusterInfoService(@Autowired(required = false)
                                     Ignite ignite) {
        this.ignite = ignite;
    }

    @Override
    public CompletableFuture<ClusterInfo> getClusterInfo() {
        if (ignite != null) {
            // Get cluster group for all server nodes
            ClusterGroup servers = ignite.cluster().forServers();
            // Get all server nodes
            Collection<ClusterNode> serverNodes = servers.nodes();
            // Get the local node
            ClusterNode localNode = ignite.cluster().localNode();

            ClusterInfoBuilder clusterInfoBuilder = ClusterInfo.builder()
                                                                .clusteringEnabled(true)
                                                                .localNodeId(localNode.id().toString())
                                                                .topologyVersion(ignite.cluster().topologyVersion())
                                                                .clusterState(ignite.cluster().state().name())
                                                                .active(ignite.cluster().state().active());

            if (serverNodes.isEmpty()) {
                log.trace("No server nodes found");
                clusterInfoBuilder.serverNodeCount(0)
                                  .nodes(Collections.emptyList());
            } else {
                log.trace("Server nodes found: {}", serverNodes.size());

                // Map cluster nodes to NodeInfo objects
                List<NodeInfo> nodeInfos = serverNodes.stream()
                        .map(node -> mapToNodeInfo(node, localNode.id()))
                        .collect(Collectors.toList());

                // Build and return ClusterInfo
                clusterInfoBuilder.serverNodeCount(serverNodes.size())
                                  .nodes(nodeInfos);

            }

            log.trace("Returning cluster info: {}", clusterInfoBuilder.build());
            return CompletableFuture.completedFuture(clusterInfoBuilder.build());
        } else {
            log.trace("Clustering disabled, returning static cluster info");
            return CompletableFuture.completedFuture(ClusterInfo.builder()
                    .clusteringEnabled(false)
                    .localNodeId("")
                    .serverNodeCount(0)
                    .topologyVersion(0)
                    .clusterState("INACTIVE")
                    .active(false)
                    .nodes(Collections.emptyList())
                    .build());
        }

    }

    /**
     * Maps an Ignite ClusterNode to a NodeInfo domain object.
     * 
     * @param node        the cluster node to map
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
                .attributes(attributes)
                .version(node.version().toString())
                .build();
    }
}
