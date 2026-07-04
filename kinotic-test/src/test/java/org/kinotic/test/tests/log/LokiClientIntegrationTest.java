package org.kinotic.test.tests.log;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.internal.api.services.DefaultLokiClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import reactor.core.Disposable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercises {@link DefaultLokiClient} against a real multi-tenant Loki: query_range and tail
 * round-trips, and tenant isolation via the X-Scope-OrgID header. Skips when Docker is
 * unavailable.
 */
class LokiClientIntegrationTest {

    private static final DockerImageName LOKI_IMAGE = DockerImageName.parse("grafana/loki:3.3.2");

    private static GenericContainer<?> loki;
    private static Vertx vertx;
    private static DefaultLokiClient lokiClient;
    private static String lokiUrl;
    private static final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void setup() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                               "Docker is required for the Loki integration test");

        loki = new GenericContainer<>(LOKI_IMAGE)
                .withExposedPorts(3100)
                .withCommand("-config.file=/etc/loki/local-config.yaml",
                             "-auth.enabled=true",
                             "-querier.multi-tenant-queries-enabled=true")
                .waitingFor(Wait.forHttp("/ready").forPort(3100)
                                .withStartupTimeout(Duration.ofMinutes(3)));
        loki.start();

        lokiUrl = "http://" + loki.getHost() + ":" + loki.getMappedPort(3100);
        vertx = Vertx.vertx();
        KinoticDomainProperties properties = new KinoticDomainProperties();
        properties.getDomain().getLoki().setUrl(lokiUrl);
        lokiClient = new DefaultLokiClient(vertx, properties);
        lokiClient.start();
    }

    @AfterAll
    static void tearDown() {
        if (lokiClient != null) {
            lokiClient.stop();
        }
        if (vertx != null) {
            vertx.close();
        }
        if (loki != null) {
            loki.stop();
        }
    }

    @Test
    void queryRangeIsIsolatedByTenant() throws Exception {
        String markerA = "marker-a-" + UUID.randomUUID();
        String markerB = "marker-b-" + UUID.randomUUID();
        push("org-a", "wl-a", markerA);
        push("org-b", "wl-b", markerB);

        // Ingested lines become queryable asynchronously
        awaitQueryContains("org-a", "wl-a", markerA);
        awaitQueryContains("org-b", "wl-b", markerB);

        // Each tenant sees only its own streams, even when naming the other's workload
        assertFalse(queryRange("org-a", "wl-b").contains(markerB));
        assertFalse(queryRange("org-b", "wl-a").contains(markerA));
    }

    @Test
    void tailStreamsNewLines() throws Exception {
        String marker = "tail-marker-" + UUID.randomUUID();
        CompletableFuture<String> received = new CompletableFuture<>();

        Disposable subscription = lokiClient.tail("org-a", "{workload_id=\"wl-tail\"}")
                .subscribe(frame -> {
                              if (frame.toString().contains(marker)) {
                                  received.complete(frame.toString());
                              }
                          },
                          received::completeExceptionally);
        try {
            // The WebSocket may still be connecting; keep pushing fresh lines until one arrives
            for (int i = 0; i < 40 && !received.isDone(); i++) {
                push("org-a", "wl-tail", marker + " line " + i);
                Thread.sleep(500);
            }
            assertTrue(received.get(5, TimeUnit.SECONDS).contains(marker));
        } finally {
            subscription.dispose();
        }
    }

    private static void push(String tenant, String workloadId, String line) throws Exception {
        long timestampNs = System.currentTimeMillis() * 1_000_000L;
        String body = "{\"streams\":[{\"stream\":{\"workload_id\":\"" + workloadId + "\"}," +
                      "\"values\":[[\"" + timestampNs + "\",\"" + line + "\"]]}]}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(lokiUrl + "/loki/api/v1/push"))
                                         .header("Content-Type", "application/json")
                                         .header("X-Scope-OrgID", tenant)
                                         .POST(HttpRequest.BodyPublishers.ofString(body))
                                         .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(204, response.statusCode(), response.body());
    }

    private static String queryRange(String tenant, String workloadId) throws Exception {
        long now = System.currentTimeMillis();
        return lokiClient.queryRange(tenant, "{workload_id=\"" + workloadId + "\"}",
                                     now - 300_000, now + 60_000, 100)
                         .toCompletionStage().toCompletableFuture()
                         .get(10, TimeUnit.SECONDS)
                         .toString();
    }

    private static void awaitQueryContains(String tenant, String workloadId, String marker) throws Exception {
        for (int i = 0; i < 30; i++) {
            if (queryRange(tenant, workloadId).contains(marker)) {
                return;
            }
            Thread.sleep(500);
        }
        fail("Line pushed to tenant '" + tenant + "' never became queryable");
    }
}
