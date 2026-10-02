package org.kinotic.test.tests.log;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kinotic.management.api.config.ManagementApiProperties;
import org.kinotic.management.internal.api.services.telemetry.DefaultLokiClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import reactor.core.Disposable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercises {@link DefaultLokiClient} against a real multi-tenant Loki: query_range, tail and delete
 * round-trips, and tenant isolation via the X-Scope-OrgID header. Loki runs the way the compose file
 * runs it, with the compactor's retention and delete request store the deletion API needs. Skips when
 * Docker is unavailable.
 */
class LokiClientIntegrationTest {

    private static GenericContainer<?> loki;
    private static Vertx vertx;
    private static DefaultLokiClient lokiClient;
    private static String lokiUrl;
    private static final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void setup() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                               "Docker is required for the Loki integration test");

        loki = new GenericContainer<>(lokiImageFromCompose())
                .withExposedPorts(3100)
                .withCommand("-config.file=/etc/loki/local-config.yaml",
                             "-auth.enabled=true",
                             "-querier.multi-tenant-queries-enabled=true",
                             "-compactor.retention-enabled=true",
                             "-compactor.delete-request-store=filesystem",
                             "-compactor.delete-request-cancel-period=1h",
                             "-compactor.working-directory=/tmp/loki/compactor")
                .waitingFor(Wait.forHttp("/ready").forPort(3100)
                                .withStartupTimeout(Duration.ofMinutes(3)));
        loki.start();

        lokiUrl = "http://" + loki.getHost() + ":" + loki.getMappedPort(3100);
        vertx = Vertx.vertx();
        lokiClient = new DefaultLokiClient(vertx, new ManagementApiProperties().setLokiUrl(lokiUrl));
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

        Disposable subscription = lokiClient.tail("org-a", "{workload_id=\"wl-tail\"}", System.currentTimeMillis())
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

    @Test
    void tailStreamsAFrameLargerThanTheWebSocketDefault() throws Exception {
        String marker = "large-tail-marker-" + UUID.randomUUID();
        // Under Loki's 256 KB max_line_size, and sent by Loki as one frame well past Vert.x's 64 KB default
        String line = marker + " " + "x".repeat(200_000);
        CompletableFuture<String> received = new CompletableFuture<>();

        Disposable subscription = lokiClient.tail("org-a", "{workload_id=\"wl-large-tail\"}", System.currentTimeMillis())
                .subscribe(frame -> {
                              if (frame.toString().contains(marker)) {
                                  received.complete(frame.toString());
                              }
                          },
                          received::completeExceptionally);
        try {
            // The WebSocket may still be connecting; keep pushing until the line arrives or the tail fails
            for (int i = 0; i < 40 && !received.isDone(); i++) {
                push("org-a", "wl-large-tail", line);
                Thread.sleep(500);
            }
            assertTrue(received.get(5, TimeUnit.SECONDS).contains(line));
        } finally {
            subscription.dispose();
        }
    }

    @Test
    void tailFollowsOnFromWhereAQueryRangeEnds() throws Exception {
        String run = UUID.randomUUID().toString();
        String workloadId = "wl-handoff-" + run;
        long boundaryMs = System.currentTimeMillis() - 10_000;
        long boundaryNs = boundaryMs * 1_000_000L;
        pushAt("org-a", workloadId, "before-" + run, boundaryNs - 1_000_000L);
        pushAt("org-a", workloadId, "at-" + run, boundaryNs);
        pushAt("org-a", workloadId, "after-" + run, boundaryNs + 1_000_000L);
        awaitQueryContains("org-a", workloadId, "after-" + run);

        String history = lokiClient.queryRange("org-a", "{workload_id=\"" + workloadId + "\"}",
                                               boundaryMs - 60_000, boundaryMs, 100)
                                   .toCompletionStage().toCompletableFuture()
                                   .get(10, TimeUnit.SECONDS)
                                   .toString();
        assertTrue(history.contains("before-" + run));
        assertFalse(history.contains("at-" + run));
        assertFalse(history.contains("after-" + run));

        StringBuffer frames = new StringBuffer();
        CompletableFuture<Void> replayed = new CompletableFuture<>();
        Disposable subscription = lokiClient.tail("org-a", "{workload_id=\"" + workloadId + "\"}", boundaryMs)
                .subscribe(frame -> {
                              frames.append(frame);
                              if (frames.toString().contains("at-" + run) && frames.toString().contains("after-" + run)) {
                                  replayed.complete(null);
                              }
                          },
                          replayed::completeExceptionally);
        try {
            replayed.get(15, TimeUnit.SECONDS);
            assertFalse(frames.toString().contains("before-" + run));
        } finally {
            subscription.dispose();
        }
    }

    @Test
    void deleteRemovesAWorkloadsLinesFromItsTenantAlone() throws Exception {
        // a querier loads a tenant's delete requests at its first query for the tenant and reloads them
        // every five minutes, so the tenant that deletes is one no other test queries, and it is first
        // queried once the delete is registered
        String tenant = "org-c";
        String deleted = "delete-marker-" + UUID.randomUUID();
        String kept = "kept-marker-" + UUID.randomUUID();
        push(tenant, "wl-delete", deleted);
        push(tenant, "wl-keep", kept);
        push("org-b", "wl-delete", deleted);
        awaitQueryContains("org-b", "wl-delete", deleted);

        lokiClient.delete(tenant, "{workload_id=\"wl-delete\"}", 0, System.currentTimeMillis())
                  .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);

        awaitQueryContains(tenant, "wl-keep", kept);
        assertFalse(queryRange(tenant, "wl-delete").contains(deleted), "the deleted workload's lines are filtered out");
        assertTrue(queryRange("org-b", "wl-delete").contains(deleted), "the same workload id in another tenant stays");
    }

    /**
     * The compose file is the canonical Loki version declaration (helm pins the same tag),
     * so this test always runs against the version the platform deploys.
     */
    private static DockerImageName lokiImageFromCompose() {
        Path compose = Path.of("../deployment/docker-compose/compose-otel.yml");
        try {
            Matcher matcher = Pattern.compile("image:\\s*(grafana/loki:[\\w.-]+)")
                                     .matcher(Files.readString(compose));
            if (!matcher.find()) {
                throw new IllegalStateException("No grafana/loki image declared in " + compose);
            }
            return DockerImageName.parse(matcher.group(1));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + compose, e);
        }
    }

    private static void push(String tenant, String workloadId, String line) throws Exception {
        pushAt(tenant, workloadId, line, System.currentTimeMillis() * 1_000_000L);
    }

    private static void pushAt(String tenant, String workloadId, String line, long timestampNs) throws Exception {
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
