package org.kinotic.continuum.api.config;

/**
 * Constants for cluster discovery types
 */
public enum IgniteClusterDiscoveryType {
    /**
     * Local/single-node mode - no clustering
     * Use for development and single-instance deployments
     */
    LOCAL,
    /**
     * Shared filesystem discovery - uses static IP addresses
     * Use for Docker Compose, Docker Swarm, or VM environments
     * Requires clusterSharedFsAddresses to be configured
     */
    SHAREDFS,
    /**
     * Kubernetes discovery - uses Kubernetes API for node discovery
     * Use for Kubernetes/OpenShift deployments
     * Requires clusterKubernetesNamespace and clusterKubernetesServiceName
     */
    KUBERNETES

}
