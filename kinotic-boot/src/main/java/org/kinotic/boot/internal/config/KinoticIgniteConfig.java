

package org.kinotic.boot.internal.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.ignite.IgniteSystemProperties;
import org.apache.ignite.calcite.CalciteQueryEngineConfiguration;
import org.apache.ignite.configuration.*;
import org.apache.ignite.failure.FailureHandler;
import org.apache.ignite.failure.NoOpFailureHandler;
import org.apache.ignite.failure.StopNodeOrHaltFailureHandler;
import org.apache.ignite.kubernetes.configuration.KubernetesConnectionConfiguration;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.DiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.kubernetes.TcpDiscoveryKubernetesIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.sharedfs.TcpDiscoverySharedFsIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.kinotic.boot.api.config.KinoticProperties;
import org.kinotic.boot.api.config.IgniteClusterDiscoveryType;
import org.kinotic.boot.api.config.IgniteProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.apache.ignite.failure.FailureType.*;

/**
 * Class provides environment agnostic configuration for Ignite
 * Created by navid on 5/13/16.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(value = "continuum.disableClustering", havingValue = "false", matchIfMissing = true)
public class KinoticIgniteConfig {

    @Autowired
    private KinoticProperties kinoticProperties;

    @Autowired
    private IgniteProperties igniteProperties;

    @Autowired(required = false)
    private List<CacheConfiguration<?, ?>> caches;

    @Autowired(required = false)
    private List<DataRegionConfiguration> dataRegions;

    /**
     * Create the appropriate IP finder based on the configured discovery type
     */
    @ConditionalOnMissingBean
    @Bean
    public DiscoverySpi tcpDiscoverySpi() {
        TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi();

        IgniteClusterDiscoveryType discoveryType = igniteProperties.getDiscoveryType();
        switch (discoveryType) {
            case IgniteClusterDiscoveryType.LOCAL:
                discoverySpi.setIpFinder(createLocalIpFinder());
                break;
            case IgniteClusterDiscoveryType.SHAREDFS:
                discoverySpi.setIpFinder(createSharedFsIpFinder());
                break;
            case IgniteClusterDiscoveryType.KUBERNETES:
                discoverySpi.setIpFinder(createKubernetesIpFinder());
                break;
            default:
                log.warn("Unknown cluster discovery type: {}, defaulting to LOCAL", discoveryType);
                discoverySpi.setIpFinder(createLocalIpFinder());
        }

        discoverySpi.setJoinTimeout(igniteProperties.getJoinTimeoutMs());
        discoverySpi.setLocalPort(igniteProperties.getDiscoveryPort());

        if(StringUtils.isNotBlank(igniteProperties.getLocalAddress())){
            discoverySpi.setLocalAddress(igniteProperties.getLocalAddress());
        }

        return discoverySpi;
    }

    /**
     * Nodes communicate with each other over this port
     **/
    @ConditionalOnMissingBean
    @Bean
    public TcpCommunicationSpi tcpCommunicationSpi() {
        TcpCommunicationSpi communicationSpi = new TcpCommunicationSpi();
        communicationSpi.setLocalPort(igniteProperties.getCommunicationPort());
        return communicationSpi;
    }

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Bean
    public IgniteConfiguration igniteConfiguration(DiscoverySpi discoverySpi,
            TcpCommunicationSpi tcpCommunicationSpi,
            FailureHandler failureHandler) {
        // Set up a few system schema Ignite uses
        System.setProperty(IgniteSystemProperties.IGNITE_NO_ASCII, "true");// Turn off ignite console banner

        // Ignite is shutdown by Spring during application context shutdown. This is
        // done because of config in ContinuumIgniteBootstrap
        System.setProperty(IgniteSystemProperties.IGNITE_NO_SHUTDOWN_HOOK, "true");// keep from shutting down before our
                                                                                   // code

        IgniteConfiguration cfg = new IgniteConfiguration();

        cfg.setGridLogger(new Slf4jLogger());

        // Turn Stuff OFF
        cfg.setMetricsLogFrequency(0);// Metrics Logging to Console

        // Override default discovery SPI.
        if (discoverySpi != null) {
            cfg.setDiscoverySpi(discoverySpi);
        }

        // Override default communication SPI.
        if (tcpCommunicationSpi != null) {
            cfg.setCommunicationSpi(tcpCommunicationSpi);
        }

        // Setup calcite sql engine
        cfg.setSqlConfiguration(
                new SqlConfiguration().setQueryEnginesConfiguration(
                        new CalciteQueryEngineConfiguration().setDefault(true)));

        DataStorageConfiguration dataStorageConfiguration = new DataStorageConfiguration();

        // setup default memory region based on continuum config
        dataStorageConfiguration.getDefaultDataRegionConfiguration()
                .setInitialSize(kinoticProperties.getMaxOffHeapMemory() / 2)
                .setMaxSize(kinoticProperties.getMaxOffHeapMemory());

        if (dataRegions != null && !dataRegions.isEmpty()) {
            // Add other configured data regions
            DataRegionConfiguration[] configs = dataRegions.toArray(new DataRegionConfiguration[0]);
            dataStorageConfiguration.setDataRegionConfigurations(configs);
        }

        cfg.setDataStorageConfiguration(dataStorageConfiguration);

        // Ignite Cache configurations
        if (caches != null && !caches.isEmpty()) {
            CacheConfiguration<?, ?>[] cacheConfigs = caches.toArray(new CacheConfiguration[0]);
            cfg.setCacheConfiguration(cacheConfigs);
        }

        cfg.setFailureHandler(failureHandler);

        cfg.setWorkDirectory(kinoticProperties.getIgnite().getWorkDirectory());

        cfg.setAsyncContinuationExecutor(Runnable::run);

        return cfg;
    }

    @Bean
    @Profile("development")
    FailureHandler noopFailureHandler() {
        NoOpFailureHandler ret = new NoOpFailureHandler();
        ret.setIgnoredFailureTypes(Collections.unmodifiableSet(EnumSet.of(SEGMENTATION,
                SYSTEM_WORKER_TERMINATION,
                SYSTEM_WORKER_BLOCKED,
                CRITICAL_ERROR,
                SYSTEM_CRITICAL_OPERATION_TIMEOUT)));
        return ret;
    }

    @Bean
    @Profile("!development")
    FailureHandler haltFailureHandler() {
        return new StopNodeOrHaltFailureHandler();
    }

    /**
     * Create local/VM IP finder for single-node or testing
     */
    private TcpDiscoveryIpFinder createLocalIpFinder() {
        log.info("Configuring LOCAL discovery (single-node mode)");

        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        if(StringUtils.isNotBlank(igniteProperties.getLocalAddresses())){
            ipFinder.setAddresses(List.of(igniteProperties.getLocalAddresses().split(",")));
        } else {
            ipFinder.setAddresses(List.of("127.0.0.1:" + igniteProperties.getDiscoveryPort()));
        }

        return ipFinder;
    }

    /**
     * Create shared filesystem (static IP) IP finder for Docker/VM environments
     */
    private TcpDiscoveryIpFinder createSharedFsIpFinder() {
        String configuredPath = igniteProperties.getSharedFsPath();
        log.info("Configuring SHAREDFS discovery with path: {}", configuredPath);

        TcpDiscoverySharedFsIpFinder ipFinder = new TcpDiscoverySharedFsIpFinder();
        Path sharedFsPath = Path.of(configuredPath);

        try {
            if (Files.notExists(sharedFsPath)) {
                Files.createDirectories(sharedFsPath);
                log.debug("Created shared filesystem directory {}", sharedFsPath);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to prepare shared filesystem path " + sharedFsPath, e);
        }

        if (!Files.isWritable(sharedFsPath)) {
            throw new IllegalStateException("Shared filesystem path is not writable: " + sharedFsPath.toAbsolutePath());
        }

        ipFinder.setPath(sharedFsPath.toAbsolutePath().toString());

        log.info("Configured SHAREDFS discovery with writable path: {}", sharedFsPath.toAbsolutePath());

        return ipFinder;
    }

    /**
     * Create Kubernetes IP finder for K8s deployments.
     */
    private TcpDiscoveryIpFinder createKubernetesIpFinder() {
        log.info("Configuring Kubernetes discovery with namespace: {}, service: {}, include not ready addresses: {}",
                 igniteProperties.getKubernetesNamespace(), igniteProperties.getKubernetesServiceName(),
                 igniteProperties.getKubernetesIncludeNotReadyAddresses());

        KubernetesConnectionConfiguration connectionConfig = new KubernetesConnectionConfiguration();
        ;
        connectionConfig.setNamespace(igniteProperties.getKubernetesNamespace());
        connectionConfig.setServiceName(igniteProperties.getKubernetesServiceName());
        connectionConfig.setIncludeNotReadyAddresses(igniteProperties.getKubernetesIncludeNotReadyAddresses());
        if (igniteProperties.getKubernetesMasterUrl() != null) {
            connectionConfig.setMasterUrl(igniteProperties.getKubernetesMasterUrl());
        }
        if (igniteProperties.getKubernetesAccountToken() != null) {
            connectionConfig.setAccountToken(igniteProperties.getKubernetesAccountToken());
        }

        return new TcpDiscoveryKubernetesIpFinder(connectionConfig);
    }
}
