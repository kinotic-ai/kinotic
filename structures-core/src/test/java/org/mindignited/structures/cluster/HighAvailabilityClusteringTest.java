package org.mindignited.structures.cluster;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * High availability / topology validation for a docker-compose cluster.
 *
 * Focus: cluster formation and node failure/rejoin behavior (topology).
 * Verification: parse container logs for the last `servers=N` snapshot.
 */
@Slf4j
@Disabled("Disabled by default")
public class HighAvailabilityClusteringTest extends ClusterTestBase {

    @Test
    void testClusterFormation() {
        waitForAllNodesHealthy(Duration.ofMinutes(4));
        awaitServersCountAllNodesAtLeast(3, Duration.ofMinutes(6));
        assertEquals(3, getLastServersCountFromLogs(1), "Node1 should see servers=3");
        assertEquals(3, getLastServersCountFromLogs(2), "Node2 should see servers=3");
        assertEquals(3, getLastServersCountFromLogs(3), "Node3 should see servers=3");
    }

    @Test
    void testNodeFailureHandling() {
        waitForAllNodesHealthy(Duration.ofMinutes(4));
        awaitServersCountAllNodesAtLeast(3, Duration.ofMinutes(6));

        boolean stopped = false;
        try {
            stopNode(2);
            stopped = true;

            // Remaining nodes should converge to servers=2
            awaitServersCount(1, 2, Duration.ofMinutes(4));
            awaitServersCount(3, 2, Duration.ofMinutes(4));

            startNode(2);
            stopped = false;

            waitForNodeHealthy(2, Duration.ofMinutes(4));
            awaitServersCountAllNodes(3, Duration.ofMinutes(6));
        } finally {
            if (stopped) {
                try {
                    startNode(2);
                } catch (Exception e) {
                    log.warn("Failed to restart node2 during cleanup", e);
                }
            }
        }
    }
}

