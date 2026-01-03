package org.kinotic.structures.cluster;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Utility class for verifying cluster health and cache state during tests.
 * Provides methods to poll for cache eviction completion and verify cluster state.
 * 
 * Created by Navid Mitchell on 2/13/25
 */
@Slf4j
public class ClusterHealthVerifier {

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Check if a node is healthy via health check endpoint
     */
    public static boolean isNodeHealthy(String healthUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            boolean healthy = response.statusCode() == 200;
            if (healthy) {
                log.debug("Node health check passed: {}", healthUrl);
            } else {
                log.warn("Node health check failed: {} - status: {}", healthUrl, response.statusCode());
            }
            return healthy;
        } catch (Exception e) {
            log.warn("Error checking node health: {}", healthUrl, e);
            return false;
        }
    }

    /**
     * Wait for all nodes to be healthy
     */
    public static boolean waitForAllNodesHealthy(String[] healthUrls, long timeoutSeconds) {
        log.info("Waiting for all {} nodes to be healthy (timeout: {}s)", healthUrls.length, timeoutSeconds);
        
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000;
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            boolean allHealthy = true;
            
            for (String healthUrl : healthUrls) {
                if (!isNodeHealthy(healthUrl)) {
                    allHealthy = false;
                    break;
                }
            }
            
            if (allHealthy) {
                log.info("All nodes are healthy");
                return true;
            }
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        log.error("Timeout waiting for all nodes to be healthy");
        return false;
    }

    /**
     * Trigger cache eviction via REST API
     * This simulates a structure modification that triggers cache eviction
     */
    public static boolean triggerCacheEviction(String openApiUrl, String structureId, String basicAuth) {
        try {
            // For testing, we can use the structure save endpoint which triggers eviction
            String endpoint = openApiUrl + "/structures/" + structureId;
            
            log.info("Triggering cache eviction for structure: {} via {}", structureId, endpoint);
            
            // GET the structure first
            HttpRequest getRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Basic " + basicAuth)
                    .GET()
                    .build();
            
            HttpResponse<String> getResponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());
            
            if (getResponse.statusCode() != 200) {
                log.error("Failed to get structure: {}", getResponse.statusCode());
                return false;
            }
            
            // Modify and PUT it back (triggers cache eviction)
            JsonNode structure = objectMapper.readTree(getResponse.body());
            
            HttpRequest putRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Basic " + basicAuth)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(structure.toString()))
                    .build();
            
            HttpResponse<String> putResponse = httpClient.send(putRequest, HttpResponse.BodyHandlers.ofString());
            
            boolean success = putResponse.statusCode() == 200;
            if (success) {
                log.info("Cache eviction triggered successfully");
            } else {
                log.error("Failed to trigger cache eviction: {}", putResponse.statusCode());
            }
            return success;
            
        } catch (Exception e) {
            log.error("Error triggering cache eviction", e);
            return false;
        }
    }

    /**
     * Poll for cache eviction to complete on a specific node.
     * 
     * @deprecated Use {@link #waitForCacheEvictionViaClusterInfo(String, long, long)} instead
     *             for deterministic verification via ClusterInfoService.
     */
    @Deprecated
    public static boolean waitForCacheEvictionPropagation(long waitTimeMs) {
        log.info("Waiting {}ms for cache eviction to propagate across cluster", waitTimeMs);
        try {
            Thread.sleep(waitTimeMs);
            log.info("Cache eviction propagation wait complete");
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Wait interrupted", e);
            return false;
        }
    }

    /**
     * Poll for cache eviction to complete by querying a node's ClusterInfo endpoint.
     * Uses a before/after timestamp comparison to deterministically verify eviction completion.
     * 
     * This is the recommended approach for cluster tests that need to verify cache eviction
     * has been processed across nodes.
     * 
     * @param clusterInfoUrl the URL to the cluster info endpoint (e.g., "http://localhost:9090/cluster/info")
     * @param beforeTimestamp the lastCacheEvictionSuccessTimestamp captured before triggering eviction
     * @param timeoutMs maximum time to wait for eviction to complete in milliseconds
     * @return true if eviction was processed (timestamp advanced), false if timeout occurred
     */
    public static boolean waitForCacheEvictionViaClusterInfo(String clusterInfoUrl, long beforeTimestamp, long timeoutMs) {
        log.info("Polling ClusterInfo at {} for eviction timestamp > {}", clusterInfoUrl, beforeTimestamp);
        long deadline = System.currentTimeMillis() + timeoutMs;
        
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(clusterInfoUrl))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JsonNode clusterInfo = objectMapper.readTree(response.body());
                    long currentTimestamp = clusterInfo.path("lastCacheEvictionSuccessTimestamp").asLong(0);
                    
                    if (currentTimestamp > beforeTimestamp) {
                        log.info("Cache eviction verified - timestamp advanced from {} to {}", 
                                beforeTimestamp, currentTimestamp);
                        return true;
                    }
                    log.debug("Eviction not yet processed - current: {}, waiting for > {}", 
                            currentTimestamp, beforeTimestamp);
                }
                
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Wait interrupted", e);
                return false;
            } catch (Exception e) {
                log.debug("Error polling ClusterInfo: {}", e.getMessage());
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        
        log.warn("Timeout waiting for cache eviction at {}", clusterInfoUrl);
        return false;
    }

    /**
     * Verify that a query to a node returns expected results
     * This indirectly verifies cache state by executing operations
     */
    public static boolean verifyQueryResponse(String graphqlUrl, String query, String basicAuth) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(graphqlUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Basic " + basicAuth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"query\":\"" + query.replace("\"", "\\\"") + "\"}"))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            boolean success = response.statusCode() == 200;
            if (success) {
                log.debug("Query executed successfully on node");
            } else {
                log.warn("Query failed on node: {}", response.statusCode());
            }
            return success;
            
        } catch (Exception e) {
            log.error("Error executing query", e);
            return false;
        }
    }

    /**
     * Get basic auth header value (base64 encoded username:password)
     */
    public static String getBasicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        return java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
    }
}

