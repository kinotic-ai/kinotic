package org.kinotic.structures;

import org.junit.jupiter.api.TestInstance;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * Base class for tests that require Ignite clustering to be enabled.
 * 
 * Tests extending this class share the same clustering configuration. If a test
 * needs additional properties (like health server config), it may get a different
 * ApplicationContext. The @DirtiesContext ensures proper Ignite cleanup after 
 * each test class.
 * 
 * Ignite is a JVM-level singleton - only one "default" instance can exist per JVM.
 * When Spring creates a new context with different properties, the old Ignite must
 * be shut down first. @DirtiesContext(classMode = AFTER_CLASS) ensures this cleanup.
 * 
 * Note: This is a TEST-ONLY concern. In production, each JVM runs exactly
 * one Spring ApplicationContext with one Ignite instance.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class ClusterEnabledTestBase extends ElasticsearchTestBase {

    // Shared ports - computed ONCE and reused by ALL clustering tests
    // This ensures all tests get the same context configuration and share the cached context
    private static final int SHARED_DISCOVERY_PORT = findAvailablePort();
    private static final int SHARED_COMMUNICATION_PORT = findAvailablePort();

    @DynamicPropertySource
    static void registerClusterOverrides(DynamicPropertyRegistry registry) {
        registry.add("continuum.disableClustering", () -> false);
        registry.add("structures.cluster-discovery-type", () -> "LOCAL");
        
        // Bind to localhost only to prevent network interference from other Ignite nodes
        registry.add("continuum.cluster.localAddress", () -> "127.0.0.1");
        registry.add("continuum.cluster.localAddresses", () -> "127.0.0.1:" + SHARED_DISCOVERY_PORT);
        
        // Use shared ports so all clustering tests share the same context configuration
        registry.add("continuum.cluster.discoveryPort", () -> SHARED_DISCOVERY_PORT);
        registry.add("continuum.cluster.communicationPort", () -> SHARED_COMMUNICATION_PORT);
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to allocate ephemeral port for cluster", e);
        }
    }
}

