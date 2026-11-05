package org.kinotic.core.api;

import org.apache.ignite.cluster.ClusterNode;

import java.util.Set;

/**
 * The Ignite node attributes every Kinotic server node carries, which identify it to the rest of the cluster.
 */
public final class KinoticNodeAttributes {

    /**
     * The server kind the node runs, such as {@code management}, {@code system} or {@code app}: the name of its
     * {@link org.kinotic.core.api.event.ZonePartitioningService}.
     */
    public static final String SERVER_NAME = "kinotic.serverName";

    /**
     * The Kinotic version the node runs; absent when the node runs from classes rather than a packaged jar.
     */
    public static final String VERSION = "kinotic.version";

    /**
     * The OpenTelemetry service name that labels the logs, traces and metrics of the server the node runs; absent
     * when the deployment configures none.
     */
    public static final String TELEMETRY_SERVICE_NAME = "kinotic.telemetryServiceName";

    /**
     * The OpenTelemetry service instance id that labels the node's own logs, traces and metrics, apart from those of
     * the server's other nodes; absent when the deployment configures none.
     */
    public static final String TELEMETRY_SERVICE_INSTANCE_ID = "kinotic.telemetryServiceInstanceId";

    /**
     * The name of the {@link ServerType} the node's server is of; absent on a server that declares none, which runs
     * every module.
     */
    public static final String SERVER_TYPE = "kinotic.serverType";

    /**
     * Whether the node is selected by one of the given server types: its server is of one of them, or declares no
     * type and so runs every module.
     */
    public static boolean isServerType(ClusterNode node, Set<ServerType> types) {
        String serverType = node.attribute(SERVER_TYPE);
        return serverType == null || types.stream().anyMatch(type -> type.name().equals(serverType));
    }

    private KinoticNodeAttributes() {
    }
}
