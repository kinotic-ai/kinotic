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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Test configuration that starts the Kinotic stack (Elasticsearch + kinotic-migration)
 * via Docker Compose using compose.kinotic-test.yml.
 */
@Component
public class KinoticTestComposeBoostrap {
    private static final Logger log = LoggerFactory.getLogger(KinoticTestComposeBoostrap.class);

    private static final int ELASTICSEARCH_PORT = 9200;

    // Every Refresh.WaitFor write blocks until the next scheduled refresh, 1s by default
    private static final String TEST_REFRESH_INTERVAL = "50ms";

    private static volatile boolean containersReady = false;
    private static final Object containerLock = new Object();

    public static ComposeContainer COMPOSE_CONTAINER;

    static {
        log.info("KinoticTestConfiguration: Preparing Docker Compose...");

        File composeDir = resolveComposeDir();
        File mainCompose = new File(composeDir, "compose.kinotic-test.yml");

        COMPOSE_CONTAINER = new ComposeContainer(mainCompose)
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
            String esHost = KinoticTestComposeBoostrap.getElasticsearchHost();
            int esPort = KinoticTestComposeBoostrap.getElasticsearchPort();
            boolean esReady = ContainerHealthChecker.waitForContainerHealth(
                "kinotic-elasticsearch",
                () -> ContainerHealthChecker.isElasticsearchHealthy(esHost, esPort),
                60, 2000
            );
            if (!esReady) {
                throw new RuntimeException("kinotic-elasticsearch failed to become ready");
            }

            waitForKinoticMigrationToComplete();

            shortenRefreshInterval(esHost, esPort);

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
            boolean stopped = false;
            Long exitCode = null;
            try {
                InspectContainerResponse inspect =
                    dockerClient.inspectContainerCmd(containerName).exec();

                InspectContainerResponse.ContainerState state = inspect.getState();
                if (state == null) {
                    log.debug("Container '{}' has no state yet, retrying...", containerName);
                } else if (Boolean.TRUE.equals(state.getRunning())) {
                    log.debug("Container '{}' is still running...", containerName);
                } else {
                    stopped = true;
                    exitCode = state.getExitCodeLong();
                }
            } catch (NotFoundException e) {
                log.debug("Container '{}' not found yet, retrying...", containerName);
            } catch (Exception e) {
                log.warn("Error while inspecting '{}' container: {}", containerName, e.getMessage());
            }

            // Decided outside the try: throwing inside it is caught by the catch-all above, which
            // turns a failed migration into a poll that runs until the deadline
            if (stopped) {
                if (exitCode != null && exitCode == 0) {
                    log.info("Container '{}' completed successfully", containerName);
                    return;
                }
                throw new RuntimeException(
                    "kinotic-migration container exited with code " + exitCode);
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

    /**
     * Applies {@link #TEST_REFRESH_INTERVAL} to the indices the migration created and, through a lowest-priority
     * catch-all index template, to every index a test creates afterwards. A platform template that matches an
     * index outranks the catch-all, and the index keeps the default interval.
     */
    private static void shortenRefreshInterval(String esHost, int esPort) throws Exception {
        String baseUrl = "http://" + esHost + ":" + esPort;
        String settings = "{\"index\":{\"refresh_interval\":\"" + TEST_REFRESH_INTERVAL + "\"}}";
        try (HttpClient client = HttpClient.newHttpClient()) {
            putJson(client, baseUrl + "/_index_template/kinotic-test-refresh-interval",
                    "{\"index_patterns\":[\"*\"],\"priority\":1,\"template\":{\"settings\":" + settings + "}}");
            putJson(client, baseUrl + "/*/_settings?expand_wildcards=open", settings);
        }
        log.info("Elasticsearch refresh interval set to {} for the test run", TEST_REFRESH_INTERVAL);
    }

    private static void putJson(HttpClient client, String url, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                                         .header("Content-Type", "application/json")
                                         .PUT(HttpRequest.BodyPublishers.ofString(body))
                                         .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("PUT " + url + " returned " + response.statusCode() + ": " + response.body());
        }
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
