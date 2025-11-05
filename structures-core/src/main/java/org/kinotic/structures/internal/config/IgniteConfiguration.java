package org.kinotic.structures.internal.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.kubernetes.configuration.KubernetesConnectionConfiguration;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.DiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.kubernetes.TcpDiscoveryKubernetesIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.sharedfs.TcpDiscoverySharedFsIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.kinotic.structures.api.config.StructuresProperties;
import org.kinotic.structures.api.config.ClusterDiscoveryType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration for Apache Ignite cluster based on StructuresProperties.
 * Supports multiple discovery mechanisms: local, shared filesystem (static IP), and Kubernetes.
 * 
 * Created by Navid Mitchell on 2/13/25
 */
@Slf4j
@Configuration
@ConditionalOnProperty(
        value="continuum.disableClustering",
        havingValue = "false",
        matchIfMissing = true)
@RequiredArgsConstructor
public class IgniteConfiguration {

    private final StructuresProperties properties;

    /**
     * Create the appropriate IP finder based on discovery type
     */
    @Bean
    public DiscoverySpi tcpDiscoverySpi() {
        TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi();

        ClusterDiscoveryType discoveryType = properties.getClusterDiscoveryType();
        switch (discoveryType) {
            case ClusterDiscoveryType.LOCAL:
                discoverySpi.setIpFinder(createLocalIpFinder());
                break;
            case ClusterDiscoveryType.SHAREDFS:
                discoverySpi.setIpFinder(createSharedFsIpFinder());
                break;
            case ClusterDiscoveryType.KUBERNETES:
                discoverySpi.setIpFinder(createKubernetesIpFinder());
                break;
            default:
                log.warn("Unknown cluster discovery type: {}, defaulting to LOCAL", discoveryType);
                discoverySpi.setIpFinder(createLocalIpFinder());
        }

        return discoverySpi;
    }

    /**
     * Create local/VM IP finder for single-node or testing
     */
    private TcpDiscoveryIpFinder createLocalIpFinder() {
        log.info("Configuring LOCAL discovery (single-node mode)");
        
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        ipFinder.setAddresses(List.of("127.0.0.1:" + properties.getClusterDiscoveryPort()));
        
        return ipFinder;
    }

    /**
     * Create shared filesystem (static IP) IP finder for Docker/VM environments
     */
    private TcpDiscoveryIpFinder createSharedFsIpFinder() {
        log.info("Configuring SHAREDFS discovery with path: {}", 
                properties.getClusterSharedFsPath());
        
        TcpDiscoverySharedFsIpFinder ipFinder = new TcpDiscoverySharedFsIpFinder();
        ipFinder.setPath(properties.getClusterSharedFsPath());
        
        log.info("Configured SHAREDFS discovery with path: {}", properties.getClusterSharedFsPath());
        
        return ipFinder;
    }

    /**
     * Create Kubernetes IP finder for K8s deployments.
     */
    private TcpDiscoveryIpFinder createKubernetesIpFinder() {
        log.warn("Kubernetes discovery requested but not fully implemented yet.");
        log.warn("To enable: Add 'org.apache.ignite:ignite-kubernetes' dependency to build.gradle");
        log.warn("For now, falling back to local discovery. See IGNITE_KUBERNETES_TUNING.md for details.");
        
        KubernetesConnectionConfiguration connectionConfig = new KubernetesConnectionConfiguration();;
        connectionConfig.setNamespace(properties.getClusterKubernetesNamespace());
        connectionConfig.setServiceName(properties.getClusterKubernetesServiceName());
        if(properties.getClusterKubernetesMasterUrl() != null) {
            connectionConfig.setMasterUrl(properties.getClusterKubernetesMasterUrl());
        }
        if(properties.getClusterKubernetesAccountToken() != null) {
            connectionConfig.setAccountToken(properties.getClusterKubernetesAccountToken());
        }
        properties.setClusterDiscoveryPort(properties.getClusterDiscoveryPort());
        properties.setClusterCommunicationPort(properties.getClusterCommunicationPort());
        properties.setClusterJoinTimeoutMs(properties.getClusterJoinTimeoutMs());

        return new TcpDiscoveryKubernetesIpFinder(connectionConfig);
    }

}

