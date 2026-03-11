package org.kinotic.test.support.elastic;

import org.kinotic.test.support.ContainerHealthChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.time.Duration;

@Component
public class ElasticsearchTestConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchTestConfiguration.class);

    public static final ElasticsearchContainer ELASTICSEARCH_CONTAINER;
    
    // Flag to track if containers are fully ready
    private static volatile boolean containersReady = false;
    private static final Object containerLock = new Object();

    static {
        log.info("Starting TestContainers...");
        
        // Start an Elasticsearch container with a proper wait strategy
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");

        ELASTICSEARCH_CONTAINER = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:9.2.4");
        ELASTICSEARCH_CONTAINER.withEnv("discovery.type", "single-node")
                               .withEnv("xpack.security.enabled", "false");

        // We need this until this is resolved https://github.com/elastic/elasticsearch/issues/118583
        if(osName != null && osName.startsWith("Mac") && osArch != null && osArch.equals("aarch64")){
            ELASTICSEARCH_CONTAINER.withEnv("_JAVA_OPTIONS", "-XX:UseSVE=0");
        }

        ELASTICSEARCH_CONTAINER.waitingFor(
            Wait.forHttp("/_cluster/health")
                .forPort(9200)
                .withStartupTimeout(Duration.ofMinutes(3))
        );
        
        // Add a shutdown hook to ensure containers are cleaned up
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down TestContainers...");
            try {
                if (ELASTICSEARCH_CONTAINER.isRunning()) {
                    ELASTICSEARCH_CONTAINER.stop();
                    log.info("Elasticsearch container stopped");
                }
            } catch (Exception e) {
                log.warn("Error during container shutdown", e);
            }
        }));
    }
    
    /**
     * Start containers synchronously and wait for them to be ready
     */
    public static void startContainersSynchronously() {
        log.info("Starting TestContainers synchronously...");
        
        try {
            // Start Elasticsearch container
            log.info("Starting Elasticsearch container...");
            ELASTICSEARCH_CONTAINER.start();
            log.info("Elasticsearch container started successfully on {}:{}",
                ELASTICSEARCH_CONTAINER.getHost(), ELASTICSEARCH_CONTAINER.getMappedPort(9200));
            
            // Wait for containers to be ready and healthy
            waitForContainersToBeReady();
            
        } catch (Exception e) {
            log.error("Failed to start TestContainers", e);
            throw new RuntimeException("Failed to start TestContainers", e);
        }
    }
    
    /**
     * Wait for containers to be ready
     */
    private static void waitForContainersToBeReady() {
        try {
            log.info("Waiting for Elasticsearch to be fully operational...");
            
            // Wait for the Elasticsearch cluster to be healthy using the health checker
            boolean elasticsearchReady = ContainerHealthChecker.waitForContainerHealth(
                "Elasticsearch",
                () -> ContainerHealthChecker.isElasticsearchHealthy(
                    ELASTICSEARCH_CONTAINER.getHost(),
                    ELASTICSEARCH_CONTAINER.getMappedPort(9200)
                ),
                30, // max attempts
                2000 // delay between attempts in ms
            );
            
            if (!elasticsearchReady) {
                log.error("Elasticsearch failed to become ready. Container status: {}", getContainerStatus());
                throw new RuntimeException("Elasticsearch failed to become ready within expected time");
            }
            
            log.info("Elasticsearch is fully operational");
            
            // Both containers are now ready, set the flag and notify waiting threads
            synchronized (containerLock) {
                containersReady = true;
                containerLock.notifyAll();
                log.info("Both containers are now ready and healthy - notifying waiting threads");
            }
            
        } catch (Exception e) {
            log.error("Failed to wait for containers to be ready. Container status: {}", getContainerStatus(), e);
            throw new RuntimeException("Failed to wait for containers to be ready", e);
        }
    }
    
    /**
     * Wait for containers to be ready, blocking until they are
     */
    public static void waitForContainersReady() {
        synchronized (containerLock) {
            while (!containersReady) {
                try {
                    log.info("Waiting for TestContainers to be ready...");
                    containerLock.wait(30000); // Wait up to 10 seconds at a time
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for containers", e);
                }
            }
        }
    }
    
    /**
     * Check if containers are ready, throwing an exception if not
     */
    public static void ensureContainersReady() {
        if (!containersReady) {
            throw new IllegalStateException("TestContainers are not ready yet. Call waitForContainersReady() first.");
        }
    }
    
    /**
     * Check if containers are running
     */
    public static boolean areContainersRunning() {
        return ELASTICSEARCH_CONTAINER.isRunning();
    }
    
    /**
     * Check if containers are ready
     */
    public static boolean areContainersReady() {
        return containersReady;
    }
    
    /**
     * Check if the containers are healthy and ready for testing
     */
    public static boolean areContainersHealthy() {
        if (!containersReady) {
            return false;
        }
        
        try {
            // Check if Elasticsearch is healthy

            return ContainerHealthChecker.isElasticsearchHealthy(
                ELASTICSEARCH_CONTAINER.getHost(),
                ELASTICSEARCH_CONTAINER.getMappedPort(9200)
            );
            
        } catch (Exception e) {
            log.warn("Error checking container health", e);
            return false;
        }
    }
    
    /**
     * Get detailed container status information for debugging
     */
    public static String getContainerStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Container Status:\n");
        
        if (ELASTICSEARCH_CONTAINER != null) {
            status.append("Elasticsearch: ");
            status.append(ELASTICSEARCH_CONTAINER.isRunning() ? "Running" : "Not Running");
            if (ELASTICSEARCH_CONTAINER.isRunning()) {
                status.append(" on ").append(ELASTICSEARCH_CONTAINER.getHost())
                      .append(":").append(ELASTICSEARCH_CONTAINER.getMappedPort(9200));
            }
            status.append("\n");
        } else {
            status.append("Elasticsearch: Not initialized\n");
        }
        
        status.append("Containers Ready: ").append(containersReady);
        
        return status.toString();
    }


}
