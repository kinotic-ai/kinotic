package org.kinotic.test.support.kinotic;

import org.kinotic.test.support.ContainerHealthChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.ComposeContainer;

import java.io.File;

/**
 * Test configuration that starts the Kinotic stack (Elasticsearch + kinotic-server)
 * via Docker Compose using compose.kinotic-test.yml.
 * Uses Local Compose mode (requires docker compose on the host).
 */
@Component
public class KinoticTestConfiguration {
    private static final Logger log = LoggerFactory.getLogger(KinoticTestConfiguration.class);

    private static final int ELASTICSEARCH_PORT = 9200;
    private static final int KINOTIC_SERVER_UI_PORT = 9090;
    private static final int KINOTIC_SERVER_STOMP_PORT = 58503;

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

            String serverHost = KinoticTestConfiguration.getKinoticServerHost();
            int serverPort = KinoticTestConfiguration.getKinoticServerUiPort();
            boolean serverReady = ContainerHealthChecker.waitForContainerHealth(
                "kinotic-server",
                () -> ContainerHealthChecker.isKinoticServerHealthy(serverHost, serverPort),
                60, 2000
            );
            if (!serverReady) {
                throw new RuntimeException("kinotic-server failed to become ready");
            }

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

    public static String getKinoticServerHost() {
        return "127.0.0.1";
    }

    public static int getKinoticServerUiPort() {
        return KINOTIC_SERVER_UI_PORT;
    }

}
