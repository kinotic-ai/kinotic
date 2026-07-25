package org.kinotic.gateway.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import io.modelcontextprotocol.spec.McpError;
import org.kinotic.core.api.crud.CursorPage;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.McpToolDefinitionList;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.directory.ServiceDirectoryEntry;
import org.kinotic.core.api.service.ServiceIdentifier;
import org.kinotic.core.api.ServiceRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end verification of the MCP endpoint: test services published on boot land in the Elasticsearch
 * directory with their generated schemas, and the reference MCP SDK client drives {@code /mcp} over HTTP as
 * each participant type — the interop guarantee for the hand-rolled server.
 */
@SpringBootTest
@EnabledIf(value = "dockerAvailable", disabledReason = "Docker is required for the MCP e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class McpE2eTests {

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    static final String TEST_ORG_ID = "mcp-test-org";
    static final String TEST_APP_ID = "mcp-test-app";

    private static final String MCP_URL = "http://localhost:58513";
    // minted names: qualifiedName with '.' -> '_' and '~' sanitized to '_', then '-' + function
    private static final String CALCULATOR_ADD = "os-api_org_kinotic_gateway_mcp_ITestCalculatorService-add";
    private static final String CALCULATOR_JOIN = "os-api_org_kinotic_gateway_mcp_ITestCalculatorService-join";
    private static final String APP_ECHO = "app_" + TEST_ORG_ID + "_" + TEST_APP_ID + "_org_kinotic_gateway_mcp_ITestAppEchoService-echo";

    private static ElasticsearchContainer elasticsearch;

    @Autowired
    private ServiceDirectory serviceDirectory;
    @Autowired
    private ServiceRegistry serviceRegistry;

    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        elasticsearch = new ElasticsearchContainer(
                DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.2.4"))
                .withEnv("xpack.security.enabled", "false")
                .withStartupTimeout(Duration.ofMinutes(3));
        elasticsearch.start();
        registry.add("kinotic.domain.elastic-connections[0].host", elasticsearch::getHost);
        registry.add("kinotic.domain.elastic-connections[0].port", () -> elasticsearch.getMappedPort(9200));
        registry.add("kinotic.domain.elastic-connections[0].scheme", () -> "http");
    }

    @AfterAll
    static void stopElasticsearch() {
        if (elasticsearch != null) {
            elasticsearch.stop();
        }
    }

    private static McpSyncClient systemClient;
    private static McpSyncClient organizationClient;
    private static McpSyncClient applicationClient;

    @BeforeAll
    static void createClients() {
        systemClient = client(Map.of("clientId", "system-tester"));
        organizationClient = client(Map.of("clientId", "org-tester", "organizationId", TEST_ORG_ID));
        applicationClient = client(Map.of("clientId", "app-tester",
                                          "organizationId", TEST_ORG_ID,
                                          "applicationId", TEST_APP_ID));
    }

    @AfterAll
    static void closeClients() {
        for (McpSyncClient client : List.of(systemClient, organizationClient, applicationClient)) {
            if (client != null) {
                client.close();
            }
        }
    }

    private static McpSyncClient client(Map<String, String> authHeaders) {
        HttpClientStreamableHttpTransport transport =
                HttpClientStreamableHttpTransport.builder(MCP_URL)
                                                 .endpoint("/mcp")
                                                 .httpRequestCustomizer((requestBuilder, method, uri, body, context) ->
                                                         authHeaders.forEach(requestBuilder::header))
                                                 .build();
        McpSyncClient client = McpClient.sync(transport)
                                        .requestTimeout(Duration.ofSeconds(40))
                                        .build();
        client.initialize();
        return client;
    }

    private static List<String> toolNames(McpSyncClient client) {
        return client.listTools().tools().stream().map(McpSchema.Tool::name).toList();
    }

    @Test
    @Order(1)
    public void toolListingsFollowTheZoneVisibilityMatrix() {
        // the directory publishes on ApplicationReadyEvent; wait for the listing to settle
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertTrue(toolNames(systemClient).contains(CALCULATOR_ADD)));

        List<String> systemTools = toolNames(systemClient);
        assertTrue(systemTools.contains(CALCULATOR_JOIN));
        assertTrue(systemTools.contains(APP_ECHO), "system sees every zone");

        List<String> organizationTools = toolNames(organizationClient);
        assertTrue(organizationTools.contains(CALCULATOR_ADD), "org participants see os-api tools");
        assertFalse(organizationTools.contains(APP_ECHO), "org participants never see app-zone tools");

        List<String> applicationTools = toolNames(applicationClient);
        assertTrue(applicationTools.contains(APP_ECHO), "app participants see their own app zone");
        assertFalse(applicationTools.contains(CALCULATOR_ADD), "app participants never see os-api tools");
    }

    @Test
    @Order(2)
    public void toolSchemasAndHintsComeFromTheContract() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertTrue(toolNames(systemClient).contains(CALCULATOR_ADD)));

        McpSchema.Tool add = systemClient.listTools()
                                         .tools()
                                         .stream()
                                         .filter(tool -> tool.name().equals(CALCULATOR_ADD))
                                         .findFirst()
                                         .orElseThrow();
        assertEquals("Adds two numbers", add.description());
        assertEquals("Add Numbers", add.title());
        assertNotNull(add.annotations());
        assertEquals(Boolean.TRUE, add.annotations().readOnlyHint());

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) add.inputSchema().get("properties");
        assertTrue(properties.containsKey("left") && properties.containsKey("right"),
                   "schema property names come from the compiled parameter names");
    }

    @Test
    @Order(3)
    public void toolsCallDispatchesThroughTheRpcPath() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertTrue(toolNames(systemClient).contains(CALCULATOR_ADD)));

        McpSchema.CallToolResult result = systemClient.callTool(
                new McpSchema.CallToolRequest(CALCULATOR_ADD, Map.of("left", 19, "right", 23)));
        assertEquals(Boolean.FALSE, result.isError());
        assertEquals("42", ((McpSchema.TextContent) result.content().getFirst()).text());

        // the named-arguments binding rejects a name matching no parameter
        McpSchema.CallToolResult unknownArgument = systemClient.callTool(
                new McpSchema.CallToolRequest(CALCULATOR_JOIN, Map.of("first", "a", "bogus", "b")));
        assertEquals(Boolean.TRUE, unknownArgument.isError());
    }

    @Test
    @Order(4)
    public void streamingReturnTypesAreRejectedFromTheDirectory() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertTrue(toolNames(systemClient).contains(CALCULATOR_ADD)));

        // buildEntry rejected the service, so no entry exists and no tool is listed
        assertFalse(toolNames(systemClient).stream().anyMatch(name -> name.contains("ITestStreamingService")));
        Page<ServiceDirectoryEntry> entries = serviceDirectory.findEntriesScopedTo(null, null,
                                                                                   Pageable.create(0, 1000, Sort.unsorted()))
                                                              .await();
        assertTrue(entries.getContent().stream().noneMatch(entry -> entry.getName().equals("ITestStreamingService")));
    }

    @Test
    @Order(4)
    public void toolListingsPaginateWithCursors() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertTrue(toolNames(systemClient).contains(APP_ECHO)));

        // everything fits one page, so the listing carries no cursor; a garbage cursor is rejected per spec
        assertNull(systemClient.listTools().nextCursor());
        assertThrows(McpError.class, () -> systemClient.listTools("not-a-cursor"));

        // one entry per page: the echo entry sorts first by id, the calculator entry follows via the cursor
        Pageable firstPage = Pageable.create(null, 1, Sort.by("id"));
        McpToolDefinitionList pageOne = serviceDirectory.findMcpToolsCallableBy(null, null, firstPage).await();
        assertEquals(List.of(APP_ECHO), pageOne.getTools().stream().map(McpToolDefinition::getName).toList());
        assertNotNull(pageOne.getNextCursor());

        Pageable secondPage = Pageable.create(pageOne.getNextCursor(), 1, Sort.by("id"));
        McpToolDefinitionList pageTwo = serviceDirectory.findMcpToolsCallableBy(null, null, secondPage).await();
        assertEquals(List.of(CALCULATOR_ADD, CALCULATOR_JOIN),
                     pageTwo.getTools().stream().map(McpToolDefinition::getName).toList());
    }

    @Test
    @Order(5)
    public void unregisteredServicesGoOfflineAndTheirToolsDisappear() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertTrue(toolNames(applicationClient).contains(APP_ECHO)));

        serviceRegistry.unregister(new ServiceIdentifier("app." + TEST_ORG_ID + "." + TEST_APP_ID,
                                                         "org.kinotic.gateway.mcp",
                                                         "ITestAppEchoService",
                                                         null,
                                                         "1.0.0"))
                       .await();

        // the liveness updater verifies the change and flips the entry offline
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertFalse(toolNames(applicationClient).contains(APP_ECHO)));

        McpSchema.CallToolResult offline = applicationClient.callTool(
                new McpSchema.CallToolRequest(APP_ECHO, Map.of("message", "hello")));
        assertEquals(Boolean.TRUE, offline.isError());
    }
}
