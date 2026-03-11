package org.kinotic.test.support.kinotic;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import org.kinotic.test.support.ContainerHealthChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.ComposeContainer;

import java.io.File;

/**
 * Test configuration that starts the Kinotic stack (Elasticsearch + kinotic-migration)
 * via Docker Compose using compose.kinotic-test.yml.
 */
@Component
public class KinoticTestConfiguration {
    private static final Logger log = LoggerFactory.getLogger(KinoticTestConfiguration.class);

    private static final int ELASTICSEARCH_PORT = 9200;

    private static volatile boolean containersReady = false;
    private static final Object containerLock = new Object();

    public static ComposeContainer COMPOSE_CONTAINER;

    static {
        log.info("KinoticTestConfiguration: Preparing Docker Compose...");

        File composeDir = resolveComposeDir();
        File mainCompose = new File(composeDir, "compose.kinotic-test.yml");
        String osName = System.getProperty("os.name", "");
        String osArch = System.getProperty("os.arch", "");
        File[] composeFiles = osName.startsWith("Mac") && "aarch64".equals(osArch)
            ? new File[] { mainCompose, new File(composeDir, "compose.ek-m4.override.yml") }
            : new File[] { mainCompose };

        COMPOSE_CONTAINER = new ComposeContainer(composeFiles)
                .withOptions("--project-name", "kinotic-test");


        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down Kinotic Compose containers...");
            try {
                if (containersReady) {
                    COMPOSE_CONTAINER.close();
                }
            } catch (Exception e) {
                log.warn("Error during compose shutdown", e);
            }
        }));
    }

    private static File resolveComposeDir() {
        return new File("../deployment/docker-compose").getAbsoluteFile();
    }

    public static void startContainersSynchronously() {
        log.info("Starting Kinotic Docker Compose...");
        try {
            COMPOSE_CONTAINER.start();
            waitForContainersToBeReady();
        } catch (Exception e) {
            log.error("Failed to start Kinotic Compose", e);
            throw new RuntimeException("Failed to start Kinotic Compose", e);
        }
    }

    private static void waitForContainersToBeReady() {
        try {
            String esHost = KinoticTestConfiguration.getElasticsearchHost();
            int esPort = KinoticTestConfiguration.getElasticsearchPort();
            boolean esReady = ContainerHealthChecker.waitForContainerHealth(
                "kinotic-elasticsearch",
                () -> ContainerHealthChecker.isElasticsearchHealthy(esHost, esPort),
                60, 2000
            );
            if (!esReady) {
                throw new RuntimeException("kinotic-elasticsearch failed to become ready");
            }

            waitForKinoticMigrationToComplete();

            synchronized (containerLock) {
                containersReady = true;
                containerLock.notifyAll();
            }
            log.info("Kinotic Compose stack is ready");
        } catch (Exception e) {
            log.error("Failed waiting for containers", e);
            throw new RuntimeException("Failed waiting for Kinotic Compose", e);
        }
    }

    private static void waitForKinoticMigrationToComplete() {
        final String containerName = "kinotic-migration";
        final long timeoutMs = 600_000L; // 10 minutes
        final long pollIntervalMs = 2_000L;

        log.info("Waiting for '{}' container to complete migrations...", containerName);

        DockerClient dockerClient = DockerClientFactory.instance().client();
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            try {
                InspectContainerResponse inspect =
                    dockerClient.inspectContainerCmd(containerName).exec();

                InspectContainerResponse.ContainerState state = inspect.getState();
                if (state == null) {
                    log.debug("Container '{}' has no state yet, retrying...", containerName);
                } else if (Boolean.TRUE.equals(state.getRunning())) {
                    log.debug("Container '{}' is still running...", containerName);
                } else {
                    Long exitCode = state.getExitCodeLong();
                    if (exitCode != null && exitCode == 0) {
                        log.info("Container '{}' completed successfully", containerName);
                        return;
                    } else {
                        throw new RuntimeException(
                            "kinotic-migration container exited with code " + exitCode);
                    }
                }
            } catch (NotFoundException e) {
                log.debug("Container '{}' not found yet, retrying...", containerName);
            } catch (Exception e) {
                log.warn("Error while inspecting '{}' container: {}", containerName, e.getMessage());
            }

            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(
                    "Interrupted while waiting for kinotic-migration container to complete", ie);
            }
        }

        throw new RuntimeException(
            "Timed out waiting for kinotic-migration container to complete");
    }

    public static void waitForContainersReady() {
        synchronized (containerLock) {
            while (!containersReady) {
                try {
                    containerLock.wait(30000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted waiting for Kinotic Compose", e);
                }
            }
        }
    }

    public static void ensureContainersReady() {
        if (!containersReady) {
            throw new IllegalStateException("Kinotic Compose is not ready. Call waitForContainersReady() first.");
        }
    }

    public static boolean areContainersRunning() {
        return containersReady;
    }

    public static boolean areContainersReady() {
        return containersReady;
    }

    public static String getElasticsearchHost() {
        return "127.0.0.1";
    }

    public static int getElasticsearchPort() {
        return ELASTICSEARCH_PORT;
    }

}
