package org.mindignited.structures.base.keycloak;

import java.time.Duration;
import java.io.File;

import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.wait.strategy.Wait;

import dasniko.testcontainers.keycloak.KeycloakContainer;

import org.mindignited.structures.base.ContainerHealthChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class KeyloakTestConfiguration {
    private static final Logger log = LoggerFactory.getLogger(KeyloakTestConfiguration.class);

    public static final KeycloakContainer KEYCLOAK_CONTAINER;
    
    // Flag to track if containers are fully ready
    private static volatile boolean containersReady = false;
    private static final Object containerLock = new Object();

    static {

        try {
            log.info("Starting TestContainers...");

        // Start Keycloak container with proper wait strategy
        KEYCLOAK_CONTAINER = new KeycloakContainer("quay.io/keycloak/keycloak:26.0.2")
            .withAdminUsername("admin")
            .withAdminPassword("admin")
            // Keycloak serves app traffic on KC_HTTP_PORT, but health endpoints are on the
            // management interface (default port 9000). Expose both.
            .withExposedPorts(8888, 9000)
            .withEnv("KEYCLOAK_ADMIN", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
            .withEnv("KC_HEALTH_ENABLED", "true")
            .withEnv("KC_METRICS_ENABLED", "true")
            .withEnv("KC_HTTP_PORT", "8888") // Configure Keycloak to listen on port 8888 internally
            .withEnv("KC_HTTP_ENABLED", "true");
        
        // Add realm import if the file exists
        File realmFile = ResourceUtils.getFile("classpath:keycloak-realm-export.json");
        if (realmFile.exists()) {
            log.info("Using existing Keycloak realm configuration: {}", realmFile.getAbsolutePath());
            // Keycloak 26+: import-at-startup reads JSON from /opt/keycloak/data/import only when started with --import-realm.
            // Prefer bind-mount over copy-to-container (tar), which is sensitive to commons-io/commons-compress classpath issues.
            KEYCLOAK_CONTAINER
                .withFileSystemBind(
                    realmFile.getAbsolutePath(),
                    "/opt/keycloak/data/import/test-realm.json",
                    BindMode.READ_ONLY
                );
        } else {
            log.info("No Keycloak realm configuration found, using default configuration");
        }
        
        KEYCLOAK_CONTAINER.waitingFor(
            // Keycloak 26+ (Quarkus) health endpoint (requires KC_HEALTH_ENABLED=true)
            // NOTE: Path MUST start with '/' for Testcontainers HttpWaitStrategy.
            Wait.forHttp("/health/ready")
                // Health endpoints are served via the management port (default 9000).
                .forPort(9000)
                .withStartupTimeout(Duration.ofMinutes(3))
        );
        
        // Start containers synchronously to ensure they're ready before class loading completes
        startContainersSynchronously();
        
        // Add shutdown hook to ensure containers are cleaned up
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down TestContainers...");
            try {
                if (KEYCLOAK_CONTAINER != null && KEYCLOAK_CONTAINER.isRunning()) {
                    KEYCLOAK_CONTAINER.stop();
                    log.info("Keycloak container stopped");
                }
            } catch (Exception e) {
                log.warn("Error during container shutdown", e);
            }
        }));
        } catch (Exception e) {
            log.error("Failed to start TestContainers", e);
            throw new RuntimeException("Failed to start TestContainers", e);
        }
    }
    
    /**
     * Start containers synchronously and wait for them to be ready
     */
    public static void startContainersSynchronously() {
        log.info("Starting TestContainers synchronously...");
        
        try {

            // Start Keycloak container
            log.info("Starting Keycloak container...");
            KEYCLOAK_CONTAINER.start();
            log.info("Keycloak container started successfully on {}:{}",
                KEYCLOAK_CONTAINER.getHost(), KEYCLOAK_CONTAINER.getMappedPort(8888));
            
            // Wait for containers to be ready and healthy
            waitForContainersToBeReady();
            
        } catch (Exception e) {
            log.error("Failed to start TestContainers", e);
            throw new RuntimeException("Failed to start TestContainers", e);
        }
    }
    
    private static void waitForContainersToBeReady() {
        try {
            
            log.info("Waiting for Keycloak to be fully operational...");
            
            // Wait for Keycloak to be fully ready using the health checker
            boolean keycloakReady = ContainerHealthChecker.waitForContainerHealth(
                "Keycloak",
                () -> ContainerHealthChecker.isKeycloakHealthy(
                    KEYCLOAK_CONTAINER.getHost(),
                    KEYCLOAK_CONTAINER.getMappedPort(9000)
                ),
                30, // max attempts
                2000 // delay between attempts in ms
            );
            
            if (!keycloakReady) {
                log.error("Keycloak failed to become ready. Container status: {}", getContainerStatus());
                throw new RuntimeException("Keycloak failed to become ready within expected time");
            }
            
            log.info("Keycloak is fully operational");
            
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
    

    
    public static boolean areContainersRunning() {
        return KEYCLOAK_CONTAINER.isRunning();
    }
    
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
            
            // Check if Keycloak is healthy
            boolean keycloakHealthy = ContainerHealthChecker.isKeycloakHealthy(
                KEYCLOAK_CONTAINER.getHost(),
                KEYCLOAK_CONTAINER.getMappedPort(9000)
            );
            
            return keycloakHealthy;
            
        } catch (Exception e) {
            log.warn("Error checking container health", e);
            return false;
        }
    }
    
    /**
     * Wait for containers to be healthy, blocking until they are
     */
    public static void waitForContainersHealthy() {
        while (!areContainersHealthy()) {
            try {
                log.info("Waiting for containers to become healthy...");
                Thread.sleep(10000); // Wait 10 seconds between checks
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for containers to become healthy", e);
            }
        }
        log.info("All containers are healthy and ready for testing");
    }
    
    /**
     * Get detailed container status information for debugging
     */
    public static String getContainerStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Container Status:\n");
        
        if (KEYCLOAK_CONTAINER != null) {
            status.append("Keycloak: ");
            status.append(KEYCLOAK_CONTAINER.isRunning() ? "Running" : "Not Running");
            if (KEYCLOAK_CONTAINER.isRunning()) {
                status.append(" on ").append(KEYCLOAK_CONTAINER.getHost())
                      .append(":").append(KEYCLOAK_CONTAINER.getMappedPort(8888));
            }
            status.append("\n");
        } else {
            status.append("Keycloak: Not initialized\n");
        }
        
        status.append("Containers Ready: ").append(containersReady);
        
        return status.toString();
    }
    
    /**
     * Shutdown all TestContainers
     */
    public static void shutdownContainers() {
        log.info("Shutting down TestContainers...");
        
        try {

            if (KEYCLOAK_CONTAINER != null && KEYCLOAK_CONTAINER.isRunning()) {
                KEYCLOAK_CONTAINER.stop();
                log.info("Keycloak container stopped");
            }
            
            synchronized (containerLock) {
                containersReady = false;
            }
            
            log.info("All TestContainers stopped successfully");
            
        } catch (Exception e) {
            log.warn("Error during container shutdown", e);
        }
    }
    
    public static String getKeycloakUrl() {
        return "http://" + KEYCLOAK_CONTAINER.getHost() + ":" + KEYCLOAK_CONTAINER.getMappedPort(8888);
    }
    
    public static String getKeycloakAuthUrl() {
        return getKeycloakUrl() + "/realms/test";
    }

}
