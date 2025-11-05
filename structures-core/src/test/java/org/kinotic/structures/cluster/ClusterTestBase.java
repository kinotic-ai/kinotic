package org.kinotic.structures.cluster;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for cluster testing using Testcontainers.
 * Manages multi-node Structures server cluster with shared Elasticsearch instance.
 * 
 * Created by Navid Mitchell on 2/13/25
 */
@Slf4j
public abstract class ClusterTestBase {

    protected static final String STRUCTURES_IMAGE = "kinotic/structures-server:3.5.0-SNAPSHOT";
    protected static final String ELASTICSEARCH_IMAGE = "docker.elastic.co/elasticsearch/elasticsearch:8.18.1";
    
    protected static Network network;
    protected static ElasticsearchContainer elasticsearch;
    protected static List<GenericContainer<?>> structuresNodes = new ArrayList<>();
    
    protected static final int NODE_COUNT = 3;

    @BeforeAll
    @SuppressWarnings("resource") // Network and containers are closed in teardownCluster()
    public static void setupCluster() {
        log.info("Starting cluster test setup with {} nodes", NODE_COUNT);
        
        // Create shared network for all containers
        network = Network.newNetwork();
        
        // Start Elasticsearch (shared by all nodes)
        elasticsearch = new ElasticsearchContainer(ELASTICSEARCH_IMAGE)
                .withNetwork(network)
                .withNetworkAliases("elasticsearch")
                .withEnv("discovery.type", "single-node")
                .withEnv("xpack.security.enabled", "false")
                .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
                .waitingFor(Wait.forHealthcheck()
                        .withStartupTimeout(Duration.ofMinutes(2)));
        
        elasticsearch.start();
        log.info("Elasticsearch started at: {}", elasticsearch.getHttpHostAddress());
        
        // Build discovery addresses for Ignite cluster
        StringBuilder discoveryAddresses = new StringBuilder();
        for (int i = 1; i <= NODE_COUNT; i++) {
            if (i > 1) discoveryAddresses.append(",");
            discoveryAddresses.append("structures-node").append(i).append(":47500");
        }
        
        log.info("Ignite discovery addresses: {}", discoveryAddresses);
        
        // Start Structures server nodes
        for (int i = 1; i <= NODE_COUNT; i++) {
            String nodeName = "structures-node" + i;
            
            GenericContainer<?> node = new GenericContainer<>(STRUCTURES_IMAGE)
                    .withNetwork(network)
                    .withNetworkAliases(nodeName)
                    .withEnv("SPRING_PROFILES_ACTIVE", "production")
                    
                    // Apache Ignite Cluster Configuration
                    .withEnv("STRUCTURES_CLUSTER_DISCOVERY_TYPE", "sharedfs")
                    .withEnv("STRUCTURES_CLUSTER_SHARED_FS_ADDRESSES", discoveryAddresses.toString())
                    .withEnv("STRUCTURES_CLUSTER_DISCOVERY_PORT", "47500")
                    .withEnv("STRUCTURES_CLUSTER_COMMUNICATION_PORT", "47100")
                    .withEnv("STRUCTURES_CLUSTER_JOIN_TIMEOUT_MS", "30000")
                    
                    // Structures Configuration
                    .withEnv("STRUCTURES_ELASTICCONNECTIONS_0_SCHEME", "http")
                    .withEnv("STRUCTURES_ELASTICCONNECTIONS_0_HOST", "elasticsearch")
                    .withEnv("STRUCTURES_ELASTICCONNECTIONS_0_PORT", "9200")
                    .withEnv("STRUCTURES_OPEN_API_PORT", "8080")
                    .withEnv("STRUCTURES_GRAPHQL_PORT", "4000")
                    .withEnv("STRUCTURES_WEB_SERVER_PORT", "9090")
                    .withEnv("STRUCTURES_INITIALIZE_WITH_SAMPLE_DATA", "false")
                    
                    // Disable OTEL for tests (reduces overhead)
                    .withEnv("OTEL_METRICS_EXPORTER", "none")
                    .withEnv("OTEL_TRACES_EXPORTER", "none")
                    .withEnv("OTEL_LOGS_EXPORTER", "none")
                    .withExposedPorts(8080, 4000, 9090)
                    .waitingFor(Wait.forHttp("/health/")
                            .forPort(9090)
                            .withStartupTimeout(Duration.ofMinutes(3)));
            
            node.start();
            structuresNodes.add(node);
            
            log.info("Node {} started - OpenAPI: {}, GraphQL: {}, Health: {}", 
                    nodeName,
                    node.getMappedPort(8080),
                    node.getMappedPort(4000),
                    node.getMappedPort(9090));
        }
        
        // Wait for cluster formation
        waitForClusterFormation();
        
        log.info("Cluster setup complete with {} nodes", NODE_COUNT);
    }

    @AfterAll
    public static void teardownCluster() {
        log.info("Tearing down cluster");
        
        // Stop all structures nodes
        for (GenericContainer<?> node : structuresNodes) {
            try {
                node.stop();
            } catch (Exception e) {
                log.warn("Error stopping node", e);
            }
        }
        structuresNodes.clear();
        
        // Stop Elasticsearch
        if (elasticsearch != null) {
            try {
                elasticsearch.stop();
            } catch (Exception e) {
                log.warn("Error stopping Elasticsearch", e);
            }
        }
        
        // Close network
        if (network != null) {
            try {
                network.close();
            } catch (Exception e) {
                log.warn("Error closing network", e);
            }
        }
        
        log.info("Cluster teardown complete");
    }

    /**
     * Wait for all nodes to join the Ignite cluster
     */
    protected static void waitForClusterFormation() {
        log.info("Waiting for cluster formation...");
        
        // Give Ignite some time to form the cluster
        try {
            Thread.sleep(15000); // 15 seconds for cluster to stabilize
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // TODO: Could add verification via JMX or REST API to check cluster size
        
        log.info("Cluster formation wait complete");
    }

    /**
     * Get a specific node container by index (0-based)
     */
    protected static GenericContainer<?> getNode(int index) {
        if (index < 0 || index >= structuresNodes.size()) {
            throw new IllegalArgumentException("Invalid node index: " + index);
        }
        return structuresNodes.get(index);
    }

    /**
     * Get the base URL for a node's OpenAPI endpoint
     */
    protected static String getOpenApiUrl(int nodeIndex) {
        GenericContainer<?> node = getNode(nodeIndex);
        return String.format("http://%s:%d/api",
                node.getHost(),
                node.getMappedPort(8080));
    }

    /**
     * Get the base URL for a node's GraphQL endpoint
     */
    protected static String getGraphQLUrl(int nodeIndex) {
        GenericContainer<?> node = getNode(nodeIndex);
        return String.format("http://%s:%d/graphql",
                node.getHost(),
                node.getMappedPort(4000));
    }

    /**
     * Get the base URL for a node's health endpoint
     */
    protected static String getHealthUrl(int nodeIndex) {
        GenericContainer<?> node = getNode(nodeIndex);
        return String.format("http://%s:%d/health",
                node.getHost(),
                node.getMappedPort(9090));
    }
}

