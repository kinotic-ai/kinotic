package org.kinotic.boot.api.config;

/**
 * Ignite Cluster Configuration Properties
 */
public interface IgniteProperties {
    
    /**
     * Port used for Ignite node communication
     */
    Integer getCommunicationPort();

    /**
     * Port used for Ignite discovery protocol
     */
    Integer getDiscoveryPort();

    /**
     * Cluster discovery type for Apache Ignite.
     * Valid values: LOCAL, SHAREDFS, KUBERNETES
     * - LOCAL: Single-node, no clustering (default for development)
     * - SHAREDFS: Shared filesystem discovery (Docker/VM environments)
     * - KUBERNETES: Kubernetes discovery via K8s API
     * @see IgniteClusterDiscoveryType
     * @see IgniteClusterDiscoveryType#LOCAL
     * @see IgniteClusterDiscoveryType#SHAREDFS
     * @see IgniteClusterDiscoveryType#KUBERNETES
     */
    IgniteClusterDiscoveryType getDiscoveryType();
    
    /**
     * @return the work directory for Ignite
     */
    String getWorkDirectory();
    
    /**
     * Timeout in milliseconds for cluster formation/join
     */
    Long getJoinTimeoutMs(); // 0 seconds (no timeout)
    
    /**
     * Kubernetes account token for API authentication.
     * If null, uses service account token from mounted secret.
     * Only used when clusterDiscoveryType = "kubernetes"
     */
    String getKubernetesAccountToken();

    /**
     * Whether to include not ready addresses in the Kubernetes IP finder
     */
    Boolean getKubernetesIncludeNotReadyAddresses();
    
    /**
     * Kubernetes master URL for API access.
     * If null, use in-cluster configuration.
     * Only used when clusterDiscoveryType = "kubernetes"
     */
    String getKubernetesMasterUrl();
    
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
    String getLocalAddress();
    
    /**
     * Comma-delimited string of network addresses that should be considered
     * when using LOCAL clustering. Should contain a proper discovery port for address.
     * must provide known node addresses.
     */
    String getLocalAddresses();

    /**
     * The path to a directory that is reachable by all nodes, that will be used for discovery data files.
     */
    String getSharedFsPath();
    
}
