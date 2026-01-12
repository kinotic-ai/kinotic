package org.mindignited.structures.internal.api.services.impl.cluster;

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
import org.kinotic.continuum.api.config.ContinuumProperties;
import org.mindignited.structures.api.domain.cluster.ClusterInfo;
import org.mindignited.structures.api.domain.cluster.ClusterInfo.ClusterInfoBuilder;
import org.mindignited.structures.api.domain.cluster.NodeInfo;
import org.mindignited.structures.api.services.cluster.ClusterInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link ClusterInfoService}.
 * 
 * This service tracks cache eviction timestamps both from:
 * - Local eviction events (via Spring event listener)
 * - Cluster-wide eviction events (via direct method calls from
 * ClusterCacheEvictionTask)
 * 
 * This allows tests to poll the timestamp to deterministically verify eviction
 * completion
 * regardless of whether clustering is enabled or disabled.
 */
@Slf4j
@Component
public class DefaultClusterInfoService implements ClusterInfoService {

    @Autowired
    private ContinuumProperties continuumProperties;

    @Override
    public Mono<ClusterInfo> getClusterInfo() {

        boolean clusteringEnabled = !continuumProperties.isDisableClustering();
        if (clusteringEnabled) {
            Ignite ignite = Ignition.ignite();
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
                log.debug("No server nodes found");
                clusterInfoBuilder.serverNodeCount(0)
                                  .nodes(Collections.emptyList());
            } else {
                log.debug("Server nodes found: {}", serverNodes.size());

                // Map cluster nodes to NodeInfo objects
                List<NodeInfo> nodeInfos = serverNodes.stream()
                        .map(node -> mapToNodeInfo(node, localNode.id()))
                        .collect(Collectors.toList());

                // Build and return ClusterInfo
                clusterInfoBuilder.serverNodeCount(serverNodes.size())
                                  .nodes(nodeInfos);

            }

            log.trace("Returning cluster info: {}", clusterInfoBuilder.build());
            return Mono.just(clusterInfoBuilder.build());
        } else {
            log.debug("Clustering disabled, returning static cluster info");
            return Mono.just(ClusterInfo.builder()
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
                .client(node.isClient())
                .attributes(attributes)
                .version(node.version().toString())
                .build();
    }
}
