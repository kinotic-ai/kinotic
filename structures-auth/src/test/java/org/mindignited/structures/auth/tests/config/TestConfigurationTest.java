package org.mindignited.structures.auth.tests.config;

import org.junit.jupiter.api.Test;
import org.mindignited.structures.auth.config.KeyloakTestConfiguration;
import org.mindignited.structures.auth.KeycloakTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class TestConfigurationTest extends KeycloakTestBase {
    
    @Test
    public void testTestContainersStarted() {
        // Verify that TestContainers are running
        assertNotNull(KeyloakTestConfiguration.KEYCLOAK_CONTAINER);
        assertTrue(KeyloakTestConfiguration.areContainersRunning());
        assertTrue(KeyloakTestConfiguration.areContainersReady());
    }
    
    @Test
    public void testContainersAreRunning() {
        // Containers should already be ready from TestBase.setUp()
        assertTrue(KeyloakTestConfiguration.areContainersRunning(), "Containers should be running");
        assertTrue(KeyloakTestConfiguration.areContainersReady(), "Containers should be ready");
    }

    
    @Test
    public void testKeycloakUrl() {
        String url = KeyloakTestConfiguration.getKeycloakUrl();
        assertNotNull(url);
        assertTrue(url.startsWith("http://"));
        assertTrue(url.contains(":"));
    }
    
    @Test
    public void testKeycloakAuthUrl() {
        String url = KeyloakTestConfiguration.getKeycloakAuthUrl();
        assertNotNull(url);
        assertTrue(url.startsWith("http://"));
        assertTrue(url.endsWith("/realms/test"));
    }
    
    @Test
    public void testContainersAreHealthy() {
        // Containers should already be healthy from TestBase.setUp()
        assertTrue(KeyloakTestConfiguration.areContainersHealthy(), "Containers should be healthy");
    }
    
    @Test
    public void testContainerStatus() {
        String status = KeyloakTestConfiguration.getContainerStatus();
        assertNotNull(status);
        assertTrue(status.contains("Keycloak"));
        assertTrue(status.contains("Containers Ready: true"));
    }
    
    @Test
    public void testKeycloakHealthCheck() {
        // Test that Keycloak health check works
        boolean isHealthy = ContainerHealthChecker.isKeycloakHealthy(
            KeyloakTestConfiguration.KEYCLOAK_CONTAINER.getHost(),
            KeyloakTestConfiguration.KEYCLOAK_CONTAINER.getMappedPort(8888)
        );
        assertTrue(isHealthy, "Keycloak should be healthy");
    }
}
