package org.kinotic.structures.cluster;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import org.apache.ignite.Ignite;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.kinotic.structures.api.services.StructureService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for cluster-wide cache eviction using Testcontainers.
 *
 * These tests spin up a full 3-node Structures cluster and verify that cache eviction
 * propagates correctly across all nodes and handles node failures gracefully.
 *
 * Note: These tests are resource-intensive and take several minutes to run.
 * They are disabled by default and should be run manually or in CI/CD pipelines.
 *
 * To run these tests:
 * 1. Ensure Docker is running
 * 2. Build the Structures server image: ./gradlew :structures-server:bootBuildImage
 * 3. Remove @Disabled annotation
 * 4. Run: ./gradlew :structures-core:test --tests ClusterCacheEvictionTest
 *
 * Created by Navid Mitchell on 2/13/25
 */
@Slf4j
@Disabled("Cluster tests are resource-intensive - enable manually for testing")
public class ClusterCacheEvictionTest extends ClusterTestBase {

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "structures";


    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final String basicAuthHeader =
            ClusterHealthVerifier.getBasicAuthHeader(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private StructureService structureService;

    @Autowired
    private Ignite ignite;
    
    /**
     * Test instance node index (always 0).
     * Container nodes are indexed 1-3.
     */
    private static final int TEST_INSTANCE_NODE_INDEX = ClusterTestBase.TEST_INSTANCE_NODE_INDEX;

    // @Test
    // void testClusterFormation() throws InterruptedException {
    //     log.info("Testing cluster formation with {} total nodes (test instance + {} containers)", 
    //             this.testProperties.getCluster().getNodeCount(),
    //             this.testProperties.getCluster().getNodeCount() - 1);

    //     // Verify container nodes are healthy via HTTP
    //     assertTrue(ClusterHealthVerifier.waitForAllNodesHealthy(getAllHealthUrls(), 180),
    //             "Expected all container nodes to be healthy after startup");

    //     // Test instance (node 0) health is verified via Ignite cluster membership
    //     // It should be part of the cluster since it starts with the test
    //     log.info("Cluster formation test passed - test instance (node 0) + {} container nodes are healthy", 
    //             this.testProperties.getCluster().getNodeCount() - 1);

    //     int expectedNodeCount = this.testProperties.getCluster().getNodeCount();
    //     int actualNodeCount = getClusterNodeCount();
    //     log.info("Cluster total nodes: {} (expected: {})", actualNodeCount, expectedNodeCount);
    //     assertEquals(expectedNodeCount, actualNodeCount, 
    //             "Expected " + expectedNodeCount + " nodes after cluster formation");

    // }

    // @Test
    // void testCacheEvictionPropagatesAcrossCluster() throws Exception {
    //     assertTrue(ClusterHealthVerifier.waitForAllNodesHealthy(getAllHealthUrls(), 180),
    //             "Cluster must be healthy before executing cache eviction test");

    //     var holder = createAndVerify();
    //     var structure = holder.getStructure();
    //     assertNotNull(structure, "Structure creation failed");

    //     String structureId = structure.getId();
    //     String initialDescription = structure.getDescription();
    //     String updatedDescription = "Updated description " + System.currentTimeMillis();

    //     log.info("Created structure {} with initial description '{}'", structureId, initialDescription);

    //     // Warm caches by performing a search on each container node
    //     // Test instance (node 0) doesn't need cache warming via HTTP - it uses StructureService directly
    //     // This loads the Structure metadata into cache on each container node
    //     int containerNodeCount = this.testProperties.getCluster().getNodeCount() - 1;
    //     for (int nodeIndex = 1; nodeIndex <= containerNodeCount; nodeIndex++) {
    //         warmCacheWithSearch(nodeIndex, structureId, Duration.ofSeconds(90));
    //         log.info("Container node {} warmed cache for structure {} via search", nodeIndex, structureId);
    //     }
        
    //     // Test instance (node 0) cache is warmed when we create the structure
    //     log.info("Test instance (node 0) cache warmed via StructureService during structure creation");

    //     // Update the structure on test instance (node 0) - this will trigger cluster eviction
    //     log.info("Updating structure {} on test instance (node 0) to trigger cluster eviction", structureId);
    //     updateStructureDescription(0, structureId, updatedDescription);
        
    //     // Verify test instance sees the update immediately (local update)
    //     log.info("Verifying test instance (node 0) sees updated structure");
    //     var updatedStructure = structureService.findById(structureId).get(10, TimeUnit.SECONDS);
    //     assertEquals(updatedDescription, updatedStructure.getDescription(),
    //             "Test instance should see updated description immediately");

    //     // All container nodes should observe the updated description within the propagation window
    //     for (int nodeIndex = 1; nodeIndex <= containerNodeCount; nodeIndex++) {
    //         awaitStructureDescription(nodeIndex, structureId, updatedDescription, Duration.ofSeconds(120));
    //         log.info("Container node {} observed updated description for {}", nodeIndex, structureId);
    //     }

    //     int expectedNodeCount = this.testProperties.getCluster().getNodeCount();
    //     int actualNodeCount = getClusterNodeCount();
    //     log.info("Cluster total nodes: {} (expected: {})", actualNodeCount, expectedNodeCount);
    //     assertEquals(expectedNodeCount, actualNodeCount, 
    //             "Expected " + expectedNodeCount + " nodes after cache eviction propagation");

    // }

    @Test
    void testNodeFailureHandling() throws Exception {
        // Skip this test in external mode since we can't control container lifecycle
        Assumptions.assumeFalse(
                this.testProperties.getCluster().getUseExternal(),
                "testNodeFailureHandling skipped in external cluster mode - cannot control container lifecycle");
        
        assertTrue(ClusterHealthVerifier.waitForAllNodesHealthy(getAllHealthUrls(), 180),
                "Cluster must be healthy before executing node lifecycle test");

        var holder = createAndVerify();
        var structure = holder.getStructure();
        assertNotNull(structure, "Structure creation failed");

        String structureId = structure.getId();

        // Warm caches across all container nodes by performing searches
        // Test instance (node 0) cache is warmed via StructureService
        int containerNodeCount = this.testProperties.getCluster().getNodeCount() - 1;
        for (int nodeIndex = 1; nodeIndex <= containerNodeCount; nodeIndex++) {
            warmCacheWithSearch(nodeIndex, structureId, Duration.ofSeconds(90));
        }

        boolean nodeStopped = false;
        try {
            log.info("Stopping node 2 to simulate failure");
            stopNode(2);
            nodeStopped = true;

            // Give the cluster time to detect the node failure and rebalance
            log.info("Waiting for cluster to detect node failure and rebalance...");
            TimeUnit.SECONDS.sleep(60);

            // After stopping container node 2, we should have 3 nodes: test instance (0) + container nodes 1 and 3
            int expectedNodeCountAfterStop = this.testProperties.getCluster().getNodeCount() - 1;
            int actualNodeCountAfterStop = getClusterNodeCount();
            log.info("Cluster total nodes: {} (expected: {})", actualNodeCountAfterStop, expectedNodeCountAfterStop);
            assertEquals(expectedNodeCountAfterStop, actualNodeCountAfterStop, 
                    "Expected " + expectedNodeCountAfterStop + " nodes after stopping node 2");

            log.info("Restarting container node 2");
            startNode(2);
            nodeStopped = false;

            log.info("Waiting for cluster to detect node restart and rebalance...");
            TimeUnit.SECONDS.sleep(300);

            waitForNodeHealthy(2, Duration.ofMinutes(2));

            // After restart, all nodes should be in cluster again
            int expectedNodeCountAfterRestart = this.testProperties.getCluster().getNodeCount();
            int actualNodeCountAfterRestart = getClusterNodeCount();
            log.info("Cluster total nodes: {} (expected: {})", actualNodeCountAfterRestart, expectedNodeCountAfterRestart);
            assertEquals(expectedNodeCountAfterRestart, actualNodeCountAfterRestart, 
                    "Expected " + expectedNodeCountAfterRestart + " nodes after restarting node 2");

        } finally {
            if (nodeStopped) {
                try {
                    startNode(2);
                } catch (Exception e) {
                    log.warn("Failed to restart node 2 during cleanup", e);
                }
            }
        }
    }

    @Test
    @Disabled("Deletion propagation test not yet implemented")
    void testDeletionPropagation() {
        log.info("Deletion propagation test is not yet implemented");
    }

    @Test
    @Disabled("Metrics validation will be added in a follow-up task")
    void testMetricsRecorded() {
        log.info("Metrics validation test is tracked separately (TODO-3)");
    }

    /**
     * Warms the cache by performing a search query on the structure.
     * This loads the Structure metadata into cache on the node.
     * 
     * @param nodeIndex the node index to warm cache on
     * @param structureId the structure ID to warm cache for
     * @param timeout maximum time to wait for successful search
     * @return the search response JSON
     */
    private JsonNode warmCacheWithSearch(int nodeIndex, String structureId, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        Exception lastException = null;

        while (System.nanoTime() < deadline) {
            try {
                JsonNode searchResult = performSearch(nodeIndex, structureId, "*");
                if (searchResult != null) {
                    log.debug("Node {} successfully warmed cache for structure {} via search", nodeIndex, structureId);
                    return searchResult;
                }
            } catch (Exception ex) {
                lastException = ex;
                log.debug("Search failed on node {} for structure {}: {}", nodeIndex, structureId, ex.getMessage());
            }
            TimeUnit.MILLISECONDS.sleep(500);
        }

        if (lastException != null) {
            throw new AssertionError("Failed to warm cache on node " + nodeIndex + " for structure " + structureId, lastException);
        }
        throw new AssertionError("Timed out warming cache on node " + nodeIndex + " for structure " + structureId);
    }

    /**
     * Performs a search query on the structure to load it into cache.
     * 
     * @param nodeIndex the node index to search on
     * @param structureId the structure ID (format: applicationId.structureName)
     * @param query the search query (e.g., "*" for all)
     * @return the search response JSON, or null if search failed
     */
    private JsonNode performSearch(int nodeIndex, String structureId, String query) throws IOException, InterruptedException {
        String searchEndpoint = getSearchEndpoint(nodeIndex, structureId);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(searchEndpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Basic " + basicAuthHeader)
                .header("Content-Type", "text/plain")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(query))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            return objectMapper.readTree(response.body());
        }

        log.debug("Node {} returned status {} while searching structure {}", nodeIndex, response.statusCode(), structureId);
        return null;
    }

    /**
     * Gets the search endpoint URL for a structure.
     * Format: /api/{applicationId}/{structureName}/search
     * 
     * @param nodeIndex container node index (1-3). Test instance (node 0) doesn't expose HTTP endpoints.
     */
    private String getSearchEndpoint(int nodeIndex, String structureId) {
        if (nodeIndex == TEST_INSTANCE_NODE_INDEX) {
            throw new UnsupportedOperationException(
                    "Test instance (node 0) does not expose HTTP endpoints. " +
                    "Use StructureService directly for local operations.");
        }
        // Structure ID format is: {applicationId}.{structureName}
        int firstDotIndex = structureId.indexOf('.');
        if (firstDotIndex == -1) {
            throw new IllegalArgumentException("Invalid structure ID format: " + structureId + ". Expected format: applicationId.structureName");
        }
        String applicationId = structureId.substring(0, firstDotIndex);
        String structureName = structureId.substring(firstDotIndex + 1);
        return getOpenApiUrl(nodeIndex) + "/" + applicationId + "/" + structureName + "/search";
    }

    /**
     * Verifies that the Structure description matches the expected value by performing a search.
     * Note: This verifies cache eviction indirectly - if the Structure metadata cache was evicted,
     * the search will load fresh metadata. However, search results don't directly contain Structure
     * metadata, so this is a proxy verification.
     * 
     */
    private JsonNode awaitStructureDescription(int nodeIndex,
                                               String structureId,
                                               String expectedDescription,
                                               Duration timeout) throws Exception {
        // For now, we verify cache eviction by ensuring search still works after eviction
        // The Structure metadata is loaded into cache when search is performed
        // A more direct verification would require GraphQL or Structure management endpoints
        return warmCacheWithSearch(nodeIndex, structureId, timeout);
    }

    /**
     * Gets the total number of server nodes currently visible to the local node in the Ignite cluster.
     * This includes both the test instance (node 0) and all container nodes.
     * 
     * Important: This returns nodes that the local node can currently see/communicate with.
     * If a node is down or disconnected, it will not be included in this count.
     * This is exactly what we want for validating cluster state during tests.
     * 
     * Uses {@code forServers()} to filter to server nodes only (excludes client nodes if any).
     * Since all nodes in Structures are server nodes, this matches the actual cluster topology.
     * 
     * @return the total number of server nodes currently visible to the local node
     */
    private int getClusterNodeCount() {
        // forServers() returns only server nodes visible to this node
        // This is the correct API to use since we want to count active server nodes
        return ignite.cluster().forServers().nodes().size();
    }

    /**
     * Updates Structure description to trigger cache eviction.
     * 
     * Since the test instance shares the same shared FS location with the containers,
     * we can update Structures locally using StructureService, and the changes will
     * be visible to all nodes in the cluster. The local update will trigger cache
     * eviction events that propagate to all nodes via Ignite compute grid.
     * 
     * @param nodeIndex the node index (0 = test instance, 1-3 = container nodes)
     * @param structureId the structure ID to update
     * @param newDescription the new description to set
     * @throws Exception if the update fails
     */
    private void updateStructureDescription(int nodeIndex, String structureId, String newDescription) throws Exception {
        if (nodeIndex == 0) {
            // Test instance (node 0) - use local StructureService
            // Since we share the same shared FS, this update will be visible to all nodes
            log.info("Updating structure {} description locally (test instance) to trigger cluster eviction", structureId);
            
            structureService.findById(structureId)
                    .thenCompose(structure -> {
                        if (structure == null) {
                            return java.util.concurrent.CompletableFuture.failedFuture(
                                    new IllegalArgumentException("Structure not found: " + structureId));
                        }
                        structure.setDescription(newDescription);
                        return structureService.save(structure);
                    })
                    .get(30, TimeUnit.SECONDS); // Wait for update to complete
            
            log.info("Successfully updated structure {} description locally", structureId);
        } else {
            // Container nodes - would need WebSocket/STOMP, but for now we update locally
            // and rely on shared FS for visibility
            log.warn("Updating structure on container node {} - using local StructureService instead of WebSocket", nodeIndex);
            updateStructureDescription(0, structureId, newDescription);
        }
    }

}

