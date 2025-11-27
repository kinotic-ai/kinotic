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
import static org.junit.jupiter.api.Assertions.assertFalse;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HealthCheckNoClusterTest extends ElasticsearchTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int HEALTH_PORT = findAvailablePort();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void registerLocalHealthOverrides(DynamicPropertyRegistry registry) {
        registry.add("continuum.disableClustering", () -> true);
        registry.add("structures.enable-static-file-server", () -> true);
        registry.add("structures.web-server-port", () -> HEALTH_PORT);
        registry.add("structures.health-check-path", () -> "/health");
    }

    @Test
    void healthEndpointAvailableWithoutClusterStatusWhenClusteringDisabled() throws Exception {
        HttpResponse<String> response = awaitHealthResponse();

        assertEquals(200, response.statusCode(), "Expected health endpoint to respond with HTTP 200");

        String body = response.body();
        assertFalse(body.contains("cluster-status"), "Health payload should not include cluster-status check when clustering is disabled");

        JsonNode root = OBJECT_MAPPER.readTree(body);
        JsonNode checks = root.path("checks");
        if (checks.isArray()) {
            for (JsonNode check : checks) {
                String id = check.path("id").asText("");
                assertFalse("cluster-status".equals(id), "Unexpected cluster-status check present in health payload");
            }
        }
    }

    private HttpResponse<String> awaitHealthResponse() throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + HEALTH_PORT + "/health");
        HttpRequest request = HttpRequest.newBuilder().uri(uri).GET().build();

        IOException lastException = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
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
        throw new IllegalStateException("Health endpoint did not become ready in time");
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

