package org.kinotic.gateway.mcp;

import org.kinotic.core.api.annotations.EnableKinotic;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Boots the gateway with the Elasticsearch-backed directory for the MCP e2e: kinotic-core, kinotic-domain, and
 * the gateway itself, with the test services in this package published on startup.
 */
@SpringBootApplication
@EnableConfigurationProperties
@EnableKinotic
public class McpTestApplication {
}
