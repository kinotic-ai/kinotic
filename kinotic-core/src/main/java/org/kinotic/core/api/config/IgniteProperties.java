package org.kinotic.core.api.config;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Properties for the Ignite cluster configuration
 *
 * @author Nic Padilla
 * @since 1.0.0
 * @version 1.0.0
 * @see IgniteProperties
 * @see IgniteClusterDiscoveryType
 * @see TcpDiscoverySpi
 * @see TcpCommunicationSpi
 */
@Getter
@Setter
@Accessors(chain = true)
public class IgniteProperties {

    /**
     * Port used for Ignite node communication
     */
    private Integer communicationPort = TcpCommunicationSpi.DFLT_PORT;

    /**
     * Port used for Ignite discovery protocol
     */
    private Integer discoveryPort = TcpDiscoverySpi.DFLT_PORT;

    /**
     * Cluster discovery type for Apache Ignite.
     * Valid values: LOCAL, SHAREDFS, KUBERNETES
     * - LOCAL: Uses a static list of IP addresses for cluster discovery
     * - SHAREDFS: Shared filesystem discovery
     * - KUBERNETES: Kubernetes discovery via K8s API
     * @see IgniteClusterDiscoveryType
     * @see IgniteClusterDiscoveryType#LOCAL
     * @see IgniteClusterDiscoveryType#SHAREDFS
     * @see IgniteClusterDiscoveryType#KUBERNETES
     */
    private IgniteClusterDiscoveryType discoveryType = IgniteClusterDiscoveryType.SHAREDFS;

    /**
     * Timeout in milliseconds for cluster formation/join
     */
    private Long joinTimeoutMs = TcpDiscoverySpi.DFLT_JOIN_TIMEOUT; // 0 seconds (no timeout)

    /**
     * Kubernetes account token for API authentication.
     * If null, uses service account token from mounted secret.
     * Only used when clusterDiscoveryType = {@link IgniteClusterDiscoveryType#KUBERNETES}
     */
    private String kubernetesAccountToken = null;

    /**
     * Whether to include not ready addresses in the Kubernetes IP finder
     */
    private Boolean kubernetesIncludeNotReadyAddresses = false;

    /**
     * Kubernetes master URL for API access.
     * If null, use in-cluster configuration.
     * Only used when clusterDiscoveryType = {@link IgniteClusterDiscoveryType#KUBERNETES}
     */
    private String kubernetesMasterUrl = null;

    /**
     * Kubernetes namespace where Structures pods are deployed.
     * Only used when clusterDiscoveryType = {@link IgniteClusterDiscoveryType#KUBERNETES}
     */
    private String kubernetesNamespace = "default";

    /**
     * Kubernetes service name for Ignite discovery (headless service).
     * Only used when clusterDiscoveryType = {@link IgniteClusterDiscoveryType#KUBERNETES}
     */
    private String kubernetesServiceName = "kinotic";

    /**
     * Sets network addresses for the Discovery SPI.
     * If not provided, the value is resolved from IgniteConfiguration.getLocalHost().
     * If the latter is not set as well, the node binds to all available IP addresses of an
     * environment it's running on. If there is no a non-loopback address, then InetAddress.getLocalHost()
     * is used.
     * NOTE: You should initialize the IgniteConfiguration.getLocalHost() or getLocalAddress()
     * parameter with the network interface that will be used for inter-node communication.
     * Otherwise, the node can listen on multiple network addresses available in the
     * environment, and this can prolong node failure detection if some addresses
     * are not reachable from other cluster nodes. For instance, if the node is
     * bound to 3 network interfaces, it can take up to
     * 'IgniteConfiguration.getFailureDetectionTimeout() * 3 + getConnectionRecoveryTimeout()' milliseconds
     * for another node to detect a disconnect of the give node.
     */
    private String localAddress = null;

    /**
     * Comma-delimited string of network addresses that should be considered
     * when using LOCAL clustering. Should contain a proper discovery port for address.
     * must provide known node addresses.
     */
    private String localAddresses;

    /**
     * The path to a directory that is reachable by all nodes, that will be used for discovery data files.
     */
    private String sharedFsPath = "/tmp/structures-sharedfs";

    /**
     * The work directory for Ignite
     */
    private String workDirectory = "/tmp/ignite";

    @Override
    public String toString() {
        ToStringBuilder sb = new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("discoveryType", discoveryType)
                .append("localAddress", localAddress)
                .append("joinTimeoutMs", joinTimeoutMs)
                .append("discoveryPort", discoveryPort)
                .append("communicationPort", communicationPort);

        if (discoveryType == IgniteClusterDiscoveryType.SHAREDFS) {
            sb.append("sharedFsPath", sharedFsPath);
        }else if (discoveryType == IgniteClusterDiscoveryType.KUBERNETES) {
            sb.append("kubernetesNamespace", kubernetesNamespace)
              .append("kubernetesServiceName", kubernetesServiceName)
              .append("kubernetesIncludeNotReadyAddresses", kubernetesIncludeNotReadyAddresses);
        }else if (discoveryType == IgniteClusterDiscoveryType.LOCAL) {
            sb.append("localAddresses", localAddresses);
        }

        return sb.toString();
    }
}
