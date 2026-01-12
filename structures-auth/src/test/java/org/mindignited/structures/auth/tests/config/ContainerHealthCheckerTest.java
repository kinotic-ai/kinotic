package org.mindignited.structures.auth.tests.config;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

public class ContainerHealthCheckerTest {
    
    private static final Logger log = LoggerFactory.getLogger(ContainerHealthCheckerTest.class);

    @Test
    public void testElasticsearchHealthCheckWithInvalidHost() {
        // Test that Elasticsearch health check handles invalid host gracefully
        boolean isHealthy = ContainerHealthChecker.isElasticsearchHealthy("invalid-host", 9200);
        assertFalse(isHealthy, "Health check should return false for invalid host");
    }
    
    @Test
    public void testKeycloakHealthCheckWithInvalidHost() {
        // Test that Keycloak health check handles invalid host gracefully
        boolean isHealthy = ContainerHealthChecker.isKeycloakHealthy("invalid-host", 8080);
        assertFalse(isHealthy, "Health check should return false for invalid host");
    }
    
    @Test
    public void testHealthCheckWithInvalidHost() {
        // Test health check with invalid host (should return false, not throw exception)
        boolean isHealthy = ContainerHealthChecker.isElasticsearchHealthy("invalid-host", 9200);
        assertFalse(isHealthy, "Health check should return false for invalid host");
    }
    
    @Test
    public void testHealthCheckWithInvalidPort() {
        // Test health check with invalid port (should return false, not throw exception)
        boolean isHealthy = ContainerHealthChecker.isElasticsearchHealthy("localhost", 9999);
        assertFalse(isHealthy, "Health check should return false for invalid port");
    }
    
    @Test
    public void testWaitForContainerHealth() {
        // Test the wait mechanism with a simple health check
        boolean result = ContainerHealthChecker.waitForContainerHealth(
            "TestContainer",
            () -> true, // Always return true
            1, // Only 1 attempt needed
            100 // 100ms delay
        );
        
        assertTrue(result, "Wait should succeed for always-healthy container");
    }
    
    @Test
    public void testWaitForContainerHealthWithFailure() {
        // Test the wait mechanism with a failing health check
        boolean result = ContainerHealthChecker.waitForContainerHealth(
            "TestContainer",
            () -> false, // Always return false
            3, // 3 attempts
            100 // 100ms delay
        );
        
        assertFalse(result, "Wait should fail for always-unhealthy container");
    }
}
