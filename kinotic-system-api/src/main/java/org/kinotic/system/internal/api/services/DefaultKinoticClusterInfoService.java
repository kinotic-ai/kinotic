package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.cluster.ClusterNode;
import org.kinotic.core.api.KinoticNodeAttributes;
import org.kinotic.system.api.model.cluster.KinoticClusterInfo;
import org.kinotic.system.api.model.cluster.KinoticClusterInfo.KinoticClusterInfoBuilder;
import org.kinotic.system.api.model.cluster.KinoticNodeInfo;
import org.kinotic.system.api.services.KinoticClusterInfoService;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultKinoticClusterInfoService implements KinoticClusterInfoService {

    private final Ignite ignite;

    @Override
    public Future<KinoticClusterInfo> getClusterInfo() {
        // Get cluster group for all server nodes
        ClusterGroup servers = ignite.cluster().forServers();
        // Get all server nodes
        Collection<ClusterNode> serverNodes = servers.nodes();

        KinoticClusterInfoBuilder clusterInfoBuilder = KinoticClusterInfo.builder()
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
            List<KinoticNodeInfo> kinoticNodeInfos = serverNodes.stream()
                                                                .map(this::mapToNodeInfo)
                                                                .collect(Collectors.toList());

            // Build and return ClusterInfo
            clusterInfoBuilder.serverNodeCount(serverNodes.size())
                              .nodes(kinoticNodeInfos);

        }

        log.trace("Returning cluster info: {}", clusterInfoBuilder.build());
        return Future.succeededFuture(clusterInfoBuilder.build());
    }

    private KinoticNodeInfo mapToNodeInfo(ClusterNode node) {
        return KinoticNodeInfo.builder()
                              .nodeId(node.id().toString())
                              .order(node.order())
                              .serverName(node.attribute(KinoticNodeAttributes.SERVER_NAME))
                              .addresses(node.addresses())
                              .hostNames(node.hostNames())
                              .version(node.attribute(KinoticNodeAttributes.VERSION))
                              .telemetryServiceName(node.attribute(KinoticNodeAttributes.TELEMETRY_SERVICE_NAME))
                              .telemetryServiceInstanceId(node.attribute(KinoticNodeAttributes.TELEMETRY_SERVICE_INSTANCE_ID))
                              .build();
    }
}
