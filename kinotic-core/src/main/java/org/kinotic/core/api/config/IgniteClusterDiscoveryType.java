package org.kinotic.core.api.config;

/**
 * Constants for cluster discovery types
 */
public enum IgniteClusterDiscoveryType {
    /**
     * Uses a static list if IP addresses for cluster discovery
     */
    LOCAL, //FIXME: change to name that more correctly describes the discovery type. This is actually using the TcpDiscoveryVmIpFinder
    /**
     * Shared filesystem discovery
     */
    SHAREDFS,
    /**
     * Kubernetes discovery - uses Kubernetes API for node discovery
     * Use for Kubernetes/OpenShift deployments
     * Requires clusterKubernetesNamespace and clusterKubernetesServiceName
     */
    KUBERNETES

}
