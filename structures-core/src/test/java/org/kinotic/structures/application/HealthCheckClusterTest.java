package org.kinotic.structures.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kinotic.structures.ElasticsearchTestBase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HealthCheckClusterTest extends ElasticsearchTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int HEALTH_PORT = findAvailablePort();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void registerClusterHealthOverrides(DynamicPropertyRegistry registry) {
        registry.add("continuum.disableClustering", () -> false);
        registry.add("structures.cluster-discovery-type", () -> "LOCAL");
        registry.add("structures.enable-static-file-server", () -> true);
        registry.add("structures.web-server-port", () -> HEALTH_PORT);
        registry.add("structures.health-check-path", () -> "/health");
    }

    @Test
    void healthEndpointIncludesClusterStatusWhenClusteringEnabled() throws Exception {
        HttpResponse<String> response = awaitHealthResponse();

        assertEquals(200, response.statusCode(), "Expected health endpoint to respond with HTTP 200");

        JsonNode root = OBJECT_MAPPER.readTree(response.body());
        assertEquals("UP", root.path("status").asText(), "Overall health status should be UP");

        JsonNode checks = root.path("checks");
        assertTrue(checks.isArray(), "Health payload should contain checks array");

        boolean foundClusterCheck = false;
        for (JsonNode check : checks) {
            if ("cluster-status".equals(check.path("id").asText())) {
                System.out.println("Cluster status check: " + check.toString());
                foundClusterCheck = true;
                assertEquals("UP", check.path("status").asText(), "Cluster status check should report UP");

                JsonNode data = check.path("data");
                assertTrue(data.isObject(), "Cluster status data should be present");
                assertNotNull(data.path("clusterId").asText(null), "clusterId should be present");
                assertTrue(data.path("nodeCount").asInt(0) >= 1, "nodeCount should be at least 1");
                assertTrue(data.path("topologyVersion").asLong(0L) >= 0L, "topologyVersion should be non-negative");

                JsonNode localNode = data.path("localNode");
                assertTrue(localNode.isObject(), "localNode information should be present");
                assertNotNull(localNode.path("nodeId").asText(null), "localNode.nodeId should be present");

                JsonNode members = data.path("members");
                assertTrue(members.isArray(), "members array should be present");
                assertTrue(members.size() >= 1, "members should contain at least one entry");
            }
        }

        assertTrue(foundClusterCheck, "Expected cluster-status check when clustering is enabled");
    }

    private HttpResponse<String> awaitHealthResponse() throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + HEALTH_PORT + "/health");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).GET().build();

        IOException lastException = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body().contains("cluster-status")) {
                    return response;
                }
            } catch (IOException e) {
                lastException = e;
            }
            Thread.sleep(250);
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new IllegalStateException("Cluster health endpoint did not become ready in time");
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to allocate ephemeral port for health checks", e);
        }
    }
}


