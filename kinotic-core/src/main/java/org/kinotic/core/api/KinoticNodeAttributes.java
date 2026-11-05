package org.kinotic.core.api;

import org.apache.ignite.cluster.ClusterNode;

/**
 * The Ignite node attributes every Kinotic server node carries, which identify it to the rest of the cluster.
 */
public final class KinoticNodeAttributes {

    private static final String SERVER_TYPE_PREFIX = "kinotic.serverType.";

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
     * The name of the attribute, set to {@code true}, that marks a node of the given {@link ServerType}; a node
     * carries one for each type it is of.
     *
     * @param type the server type
     * @return the attribute name, {@code kinotic.serverType.<TYPE>}
     */
    public static String serverTypeAttribute(ServerType type) {
        return SERVER_TYPE_PREFIX + type.name();
    }

    /** Whether the node advertises itself as a server of the given type. */
    public static boolean isServerType(ClusterNode node, ServerType type) {
        return Boolean.TRUE.equals(node.attribute(serverTypeAttribute(type)));
    }

    private KinoticNodeAttributes() {
    }
}
