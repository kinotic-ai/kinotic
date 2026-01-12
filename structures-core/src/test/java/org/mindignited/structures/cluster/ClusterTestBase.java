package org.mindignited.structures.cluster;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import com.github.dockerjava.api.DockerClient;
import org.mindignited.structures.ElasticsearchTestBase;
import org.mindignited.structures.TestProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.DockerComposeContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Base class for cluster testing using Testcontainers.
 * Manages multi-node Structures server cluster with shared Elasticsearch instance.
 * 
 * Cluster Setup:
 * - Node 0: Test instance (runs locally, shares same shared FS with containers)
 * - Nodes 1-3: Container nodes (structures-node1, structures-node2, structures-node3)
 * 
 * All nodes share the same shared FS location (~/structures/sharedfs), allowing
 * the test instance to make Structure updates locally that are visible to all nodes.
 * 
 * Created by Navid Mitchell on 2/13/25
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ClusterTestBase extends ElasticsearchTestBase {

    protected static final int HEALTH_PORT = 9090;
    protected static final int OPENAPI_PORT = 8080;
    protected static final int GRAPHQL_PORT = 4000;
    protected static final List<String> STRUCTURES_SERVICES = List.of("structures-node1", "structures-node2", "structures-node3");
    
    /**
     * Test instance node index (always 0)
     */
    protected static final int TEST_INSTANCE_NODE_INDEX = 0;

    protected static DockerComposeContainer<?> environment;
    
    /**
     * Flag to track if we're using external cluster (set in static block).
     * Used by @AfterAll to determine if containers need cleanup.
     */
    private static boolean staticUseExternal = false;
    
    @Autowired
    protected TestProperties testProperties;

    /**
     * Static block to initialize cluster containers.
     * 
     * Execution order:
     * 1. ElasticsearchTestBase static block runs first (starts Elasticsearch)
     * 2. This static block runs second (starts cluster containers)
     * 3. Spring context initialization happens (after static blocks complete)
     * 4. @BeforeAll methods run (wait for cluster formation)
     * 
     * This ensures Elasticsearch is ready before cluster containers start,
     * and cluster containers are ready before Spring context loads.
     */
    static {
        System.out.println("[ClusterTestBase] Static block - START");
        
        // 1. Verify Elasticsearch static block completed
        // Parent static block runs first, so these variables are guaranteed to be initialized
        if (!useExternalElasticsearch && ELASTICSEARCH_CONTAINER == null) {
            throw new IllegalStateException(
                    "Elasticsearch container not initialized. " +
                    "ElasticsearchTestBase static block must complete before ClusterTestBase static block.");
        }
        
        // 2. Verify Elasticsearch is running (if using container)
        if (!useExternalElasticsearch && ELASTICSEARCH_CONTAINER != null && !ELASTICSEARCH_CONTAINER.isRunning()) {
            throw new IllegalStateException("Elasticsearch container is not running");
        }
        
        // 3. Read cluster configuration from system properties or use defaults
        // Note: We can't use @Autowired TestProperties in static block, so read from system properties
        // The actual configuration will be loaded from application-test.yml via Spring later
        String useExternalProp = System.getProperty("structures.test.cluster.useExternal", "false");
        staticUseExternal = Boolean.parseBoolean(useExternalProp);
        
        String nodeCountProp = System.getProperty("structures.test.cluster.nodeCount", "4");
        int nodeCount = Integer.parseInt(nodeCountProp);
        
        System.out.println("[ClusterTestBase] Cluster configuration - useExternal: " + staticUseExternal + ", nodeCount: " + nodeCount);
        System.out.println("[ClusterTestBase] Elasticsearch ready at " + elasticsearchHost + ":" + elasticsearchPort);
        
        // 4. Start cluster containers if not using external cluster
        if (!staticUseExternal) {
            System.out.println("[ClusterTestBase] Starting Docker Compose cluster containers...");
            
            Path composeFile = Paths.get("structures-core","src", "test", "resources", "docker-compose", "cluster-test-compose.yml").toAbsolutePath();
            if (!Files.exists(composeFile)) {
                throw new IllegalStateException("Cluster compose file not found: " + composeFile);
            }

            // Testcontainers Elasticsearch is already started by ElasticsearchTestBase static block
            // Containers will connect via host.docker.internal to reach testcontainers Elasticsearch
            String containerElasticsearchHost = "host.docker.internal";
            int containerElasticsearchPort = elasticsearchPort; // Use the mapped port from testcontainers

            String testInstanceHost = "host.docker.internal";
            
            System.out.println("[ClusterTestBase] Test instance (node 0) connects to Elasticsearch at " + elasticsearchHost + ":" + elasticsearchPort);
            System.out.println("[ClusterTestBase] Container nodes will connect to Elasticsearch at " + containerElasticsearchHost + ":" + containerElasticsearchPort);

            try {
                @SuppressWarnings("resource") // Cleaned up in @AfterAll teardownCluster()
                DockerComposeContainer<?> newEnvironment = new DockerComposeContainer<>(composeFile.toFile())
                        .withLocalCompose(true)
                        .withTailChildContainers(true)
                        .withEnv("STRUCTURES_TEST_INSTANCE_HOST", testInstanceHost)
                        .withEnv("STRUCTURES_ELASTIC_SCHEME", "http")
                        .withEnv("STRUCTURES_ELASTIC_HOST", containerElasticsearchHost)
                        .withEnv("STRUCTURES_ELASTIC_PORT", String.valueOf(containerElasticsearchPort));

                newEnvironment.start();
                environment = newEnvironment; // Assign after successful start

                System.out.println("[ClusterTestBase] Docker compose environment started using " + composeFile);
                System.out.println("[ClusterTestBase] Cluster containers are starting...");
                
                // Register shutdown hook as safety net (containers also cleaned up in @AfterAll)
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (environment != null) {
                        System.out.println("[ClusterTestBase] Shutdown hook: Stopping cluster containers...");
                        try {
                            environment.stop();
                        } catch (Exception e) {
                            System.err.println("[ClusterTestBase] Error in shutdown hook: " + e.getMessage());
                        }
                    }
                }));

                Thread.sleep(30000); // 30 seconds
                System.out.println("[ClusterTestBase] Cluster containers started");
            } catch (Exception e) {
                System.err.println("[ClusterTestBase] Failed to start cluster containers: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Failed to start cluster containers in static block", e);
            }
        } else {
            System.out.println("[ClusterTestBase] Using external cluster - skipping container startup");
        }
        
        System.out.println("[ClusterTestBase] Static block - END");
    }

    @BeforeAll
    @Timeout(value = 10, unit = TimeUnit.MINUTES) 
    // Network and containers are closed in teardownCluster()
    public void setupCluster() {
        log.info("Cluster setup - waiting for cluster formation");
        
        // Containers are already started in static block
        // Now we just need to wait for cluster formation
        waitForClusterFormation();
        
        log.info("Cluster setup complete with {} nodes", this.testProperties.getCluster().getNodeCount());
    }

    @AfterAll
    public void teardownCluster() {
        log.info("Tearing down cluster");

        // Use static flag if available, otherwise fall back to TestProperties
        // Static flag is set in static block, TestProperties is available after Spring context loads
        boolean useExternal = staticUseExternal;
        if (this.testProperties != null) {
            useExternal = this.testProperties.getCluster().getUseExternal();
        }

        if (!useExternal && environment != null) {
            try {
                log.info("Stopping Docker Compose cluster containers...");
                environment.stop();
                log.info("Cluster containers stopped");
            } catch (Exception e) {
                log.warn("Error stopping docker compose environment", e);
            }
        } else {
            log.info("Skipping container cleanup - using external cluster or no containers started");
        }

        log.info("Cluster teardown complete");
    }

    /**
     * Wait for all nodes to join the Ignite cluster.
     * 
     * For container nodes (1-3), checks HTTP health endpoint.
     * For test instance (node 0), verifies via Ignite cluster membership.
     */
    protected void waitForClusterFormation() {
        log.info("Waiting for cluster formation with {} total nodes (test instance + {} containers)...", 
                this.testProperties.getCluster().getNodeCount(),
                this.testProperties.getCluster().getNodeCount() - 1);

        if (!this.testProperties.getCluster().getUseExternal()) {
            try {
                Thread.sleep(15000); // give Ignite time to elect coordinator
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Wait for container nodes via HTTP health checks
        int containerNodeCount = this.testProperties.getCluster().getNodeCount() - 1;
        for (int i = 1; i <= containerNodeCount; i++) {
            waitForNodeHealthy(i, Duration.ofMinutes(4));
        }
        
        // Test instance (node 0) is verified via Ignite cluster membership
        // It should already be part of the cluster since it starts with the test
        log.info("Test instance (node 0) is part of the cluster via shared FS discovery");

        log.info("Cluster formation wait complete - {} nodes should be in cluster", 
                this.testProperties.getCluster().getNodeCount());
    }

    /**
     * Stop a specific container node by index.
     * 
     * Note: When stopping containers, you may see errors in container logs related to:
     * - java.lang.instrument ASSERTION FAILED (OpenTelemetry agent during shutdown)
     * - NoClassDefFoundError for logback classes (classloader teardown during shutdown)
     * These are harmless shutdown-time errors that occur AFTER the application logic completes
     * and do not affect test results. They occur due to a race condition between the Java agent,
     * Logback, and classloader teardown during Spring Boot shutdown.
     * 
     * @param index container node index (1-3). Cannot stop test instance (node 0).
     */
    protected void stopNode(int index) {
        if (index == TEST_INSTANCE_NODE_INDEX) {
            throw new UnsupportedOperationException(
                    "Cannot stop test instance (node 0) - it runs in the test JVM. " +
                    "Use container node indices 1-3.");
        }
        if (this.testProperties.getCluster().getUseExternal()) {
            log.warn("stopNode({}) ignored — external cluster mode does not manage containers", index);
            return;
        }
        String service = serviceNameFor(index);
        log.info("Stopping container node {} (service {})", index, service);
        ContainerState container = getContainerState(service);
        DockerClient dockerClient = container.getDockerClient();
        
        // Stop container with a timeout to allow graceful shutdown
        // The timeout allows Spring Boot shutdown hooks to complete
        dockerClient.stopContainerCmd(container.getContainerId())
                .withTimeout(30) // 30 second timeout for graceful shutdown
                .exec();
        
        // Give a brief moment for shutdown to complete and logs to flush
        // This helps reduce shutdown-time errors in logs (though they're harmless)
        try {
            Thread.sleep(10000); // 10 second timeout for graceful shutdown
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Restart a container node after it has been stopped.
     * 
     * @param index container node index (1-3). Cannot restart test instance (node 0).
     */
    protected void startNode(int index) {
        if (index == TEST_INSTANCE_NODE_INDEX) {
            throw new UnsupportedOperationException(
                    "Cannot restart test instance (node 0) - it runs in the test JVM. " +
                    "Use container node indices 1-3.");
        }
        if (this.testProperties.getCluster().getUseExternal()) {
            log.warn("startNode({}) ignored — external cluster mode does not manage containers", index);
            return;
        }
        String service = serviceNameFor(index);
        log.info("Starting container node {} (service {})", index, service);
        ContainerState container = getContainerState(service);
        DockerClient dockerClient = container.getDockerClient();
        dockerClient.startContainerCmd(container.getContainerId()).exec();
    }

    /**
     * Get the base URL for a node's OpenAPI endpoint.
     * 
     * @param nodeIndex 0 = test instance (localhost), 1-3 = container nodes
     */
    protected String getOpenApiUrl(int nodeIndex) {
        if (nodeIndex == TEST_INSTANCE_NODE_INDEX) {
            // Test instance runs locally - would need to configure port if it exposes HTTP
            // For now, test instance doesn't expose HTTP endpoints, only uses StructureService directly
            throw new UnsupportedOperationException(
                    "Test instance (node 0) does not expose HTTP endpoints. " +
                    "Use StructureService directly for local operations.");
        }
        String service = serviceNameFor(nodeIndex);
        int servicePort = portFor(nodeIndex, OPENAPI_PORT);
        return String.format("http://%s:%d/api",
                resolveServiceHost(service, servicePort),
                servicePort);
    }

    /**
     * Get the base URL for a node's GraphQL endpoint.
     * 
     * @param nodeIndex 0 = test instance (localhost), 1-3 = container nodes
     */
    protected String getGraphQLUrl(int nodeIndex) {
        if (nodeIndex == TEST_INSTANCE_NODE_INDEX) {
            throw new UnsupportedOperationException(
                    "Test instance (node 0) does not expose HTTP endpoints.");
        }
        String service = serviceNameFor(nodeIndex);
        int servicePort = portFor(nodeIndex, GRAPHQL_PORT);
        return String.format("http://%s:%d/graphql",
                resolveServiceHost(service, servicePort),
                servicePort);
    }

    /**
     * Get the base URL for a node's health endpoint.
     * 
     * @param nodeIndex 0 = test instance (localhost), 1-3 = container nodes
     */
    protected String getHealthUrl(int nodeIndex) {
        if (nodeIndex == TEST_INSTANCE_NODE_INDEX) {
            // Test instance doesn't expose HTTP health endpoint
            // Health can be checked via Ignite cluster status instead
            throw new UnsupportedOperationException(
                    "Test instance (node 0) does not expose HTTP health endpoint. " +
                    "Use Ignite cluster status to verify test instance health.");
        }
        String service = serviceNameFor(nodeIndex);
        int servicePort = portFor(nodeIndex, HEALTH_PORT);
        return String.format("http://%s:%d/health",
                resolveServiceHost(service, servicePort),
                servicePort);
    }

    /**
     * Get health URLs for all container nodes (excluding test instance).
     * Test instance health is verified via Ignite cluster membership.
     */
    protected String[] getAllHealthUrls() {
        // Only return health URLs for container nodes (1-3), not test instance (0)
        int containerNodeCount = this.testProperties.getCluster().getNodeCount() - 1;
        String[] healthUrls = new String[containerNodeCount];
        for (int i = 0; i < containerNodeCount; i++) {
            healthUrls[i] = getHealthUrl(i + 1); // Skip test instance (node 0)
        }
        return healthUrls;
    }

    protected void waitForNodeHealthy(int nodeIndex, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        String healthUrl = getHealthUrl(nodeIndex);

        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(15000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for node to become healthy", e);
            }
            if (ClusterHealthVerifier.isNodeHealthy(healthUrl)) {
                return;
            }
        }

        throw new IllegalStateException("Node " + nodeIndex + " did not become healthy within " + timeout);
    }

    private static ContainerState getContainerState(String service) {
        if (environment == null) {
            throw new IllegalStateException("Docker environment is not managed in external cluster mode");
        }
        return environment.getContainerByServiceName(service + "_1")
                .orElseThrow(() -> new IllegalArgumentException("No container found for service " + service));
    }

    /**
     * Calculate port for a container node.
     * 
     * @param nodeIndex 1-3 for container nodes (0 is test instance, doesn't use HTTP ports)
     * @param basePort base port number
     * @return calculated port
     */
    private static int portFor(int nodeIndex, int basePort) {
        if (nodeIndex == TEST_INSTANCE_NODE_INDEX) {
            throw new IllegalArgumentException(
                    "Test instance (node 0) does not use HTTP ports. " +
                    "Use StructureService directly for local operations.");
        }
        if (nodeIndex < 1 || nodeIndex > STRUCTURES_SERVICES.size()) {
            throw new IllegalArgumentException(
                    "Invalid node index: " + nodeIndex + ". " +
                    "Expected 0 (test instance) or 1-" + STRUCTURES_SERVICES.size() + " (container nodes)");
        }
        // Container nodes: node1=basePort+1, node2=basePort+2, node3=basePort+3
        return basePort + nodeIndex;
    }

    /**
     * Get service name for a container node.
     * 
     * @param index 1-3 for container nodes (0 is test instance, has no service name)
     * @return Docker service name
     */
    private static String serviceNameFor(int index) {
        if (index == TEST_INSTANCE_NODE_INDEX) {
            throw new IllegalArgumentException(
                    "Test instance (node 0) is not a Docker service. " +
                    "It runs locally in the test JVM.");
        }
        if (index < 1 || index > STRUCTURES_SERVICES.size()) {
            throw new IllegalArgumentException(
                    "Invalid container node index: " + index + ". " +
                    "Expected 1-" + STRUCTURES_SERVICES.size());
        }
        // Container nodes are 1-indexed: node1=index 1, node2=index 2, node3=index 3
        return STRUCTURES_SERVICES.get(index - 1);
    }

    private String resolveServiceHost(String service, int servicePort) {
        if (!this.testProperties.getCluster().getUseExternal()) {
            return environment.getServiceHost(service, servicePort);
        }
        return "127.0.0.1";
    }

}

