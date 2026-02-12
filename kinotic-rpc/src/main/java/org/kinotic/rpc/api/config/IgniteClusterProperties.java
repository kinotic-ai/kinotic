package org.kinotic.rpc.api.config;

/**
 * Ignite Cluster Configuration Properties
 * 
 * @author Navid Mitchell
 * @since 1.0.0
 * @version 1.0.0
 * @see IgniteClusterDiscoveryType
 * @see IgniteClusterDiscoveryType#LOCAL
 * @see IgniteClusterDiscoveryType#SHAREDFS
 * @see IgniteClusterDiscoveryType#KUBERNETES
 */
public interface IgniteClusterProperties {
    
    /**
     * Cluster discovery type for Apache Ignite.
     * Valid values: LOCAL, SHAREDFS, KUBERNETES
     * - LOCAL: Single-node, no clustering (default for development)
     * - SHAREDFS: Shared filesystem discovery (Docker/VM environments)
     * - KUBERNETES: Kubernetes discovery via K8s API
     */
    IgniteClusterDiscoveryType getDiscoveryType();

    /**
     * Timeout in milliseconds for cluster formation/join
     */
    Long getJoinTimeoutMs(); // 0 seconds (no timeout)

    /**
     * Sets network addresses for the Discovery SPI.
     * If not provided, the value is resolved from IgniteConfiguration.getLocalHost().
     * If the latter is not set as well, the node binds to all available IP addresses of an
     * environment it's running on. If there is no a non-loopback address, then InetAddress.getLocalHost()
     * is used.
     * NOTE: You should initialize the IgniteConfiguration.getLocalHost() or getLocalAddress()
     * parameter with the network interface that will be used for inter-node communication.
     * Otherwise, the node can listen on multiple network addresses available in the
     * environment and this can prolong node failures detection if some of the addresses
     * are not reachable from other cluster nodes. For instance, if the node is
     * bound to 3 network interfaces, it can take up to
     * 'IgniteConfiguration.getFailureDetectionTimeout() * 3 + getConnectionRecoveryTimeout()' milliseconds
     * for another node to detect a disconnect of the give node.
     */
    String getLocalAddress();
    
    /**
     * The path to a directory that is reachable by all nodes, that will be used for discovery data files.
     */
    String getSharedFsPath();
    
    /**
     * Kubernetes namespace where Structures pods are deployed.
     * Only used when clusterDiscoveryType = "kubernetes"
     */
    String getKubernetesNamespace();
    
    /**
     * Kubernetes service name for Ignite discovery (headless service).
     * Only used when clusterDiscoveryType = "kubernetes"
     */
    String getKubernetesServiceName();

    /**
     * Whether to include not ready addresses in the Kubernetes IP finder
     */
    Boolean getKubernetesIncludeNotReadyAddresses();
    
    /**
     * Kubernetes master URL for API access.
     * If null, uses in-cluster configuration.
     * Only used when clusterDiscoveryType = "kubernetes"
     */
    String getKubernetesMasterUrl();
    
    /**
     * Kubernetes account token for API authentication.
     * If null, uses service account token from mounted secret.
     * Only used when clusterDiscoveryType = "kubernetes"
     */
    String getKubernetesAccountToken();
    
    /**
     * Port used for Ignite discovery protocol
     */
    Integer getDiscoveryPort();

    /**
     * Comma-delimited string of network addresses that should be considered
     * when using LOCAL clustering. Should contain proper discovery port for address.
     * must provide known nodes addresses.
     */
    String getLocalAddresses();
    
    /**
     * Port used for Ignite node communication
     */
    Integer getCommunicationPort();

    // /**
    //  * Port used for Ignite JMX
    //  */
    // private Integer jmxPort = 49112;

    // /**
    //  * Port used for Ignite thin client/JDBC/ODBC
    //  */
    // private Integer thinClientPort = 10800;

    // /**
    //  * Port used for Ignite REST API
    //  */
    // private Integer restApiPort = 8080;

    // /**
    //  * Port used for Ignite control script
    //  */
    // private Integer controlScriptPort = 11211;
    
}
