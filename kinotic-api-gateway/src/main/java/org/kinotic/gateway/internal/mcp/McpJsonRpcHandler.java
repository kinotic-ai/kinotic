package org.kinotic.gateway.internal.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.CursorPage;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.security.Participant;
import org.kinotic.gateway.internal.mcp.model.JsonRpcRequest;
import org.kinotic.gateway.internal.mcp.model.JsonRpcResponse;
import org.kinotic.gateway.internal.mcp.model.McpInitializeResult;
import org.kinotic.gateway.internal.mcp.model.McpServerInfo;
import org.kinotic.gateway.internal.mcp.model.McpToolAnnotations;
import org.kinotic.gateway.internal.mcp.model.McpToolListing;
import org.kinotic.gateway.internal.mcp.model.McpToolsListResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The stateless MCP server core: dispatches a single JSON-RPC 2.0 request to the MCP methods the gateway supports
 * and renders the response. Every request is handled independently; there are no sessions and no server-initiated
 * messages.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kinotic.disableMcp", havingValue = "false", matchIfMissing = true)
public class McpJsonRpcHandler {

    private static final String LATEST_PROTOCOL_VERSION = "2025-11-25";
    private static final Set<String> SUPPORTED_PROTOCOL_VERSIONS = Set.of("2025-03-26", "2025-06-18", LATEST_PROTOCOL_VERSION);
    private static final int TOOL_LIST_PAGE_SIZE = 1000;

    private static final int PARSE_ERROR = -32700;
    private static final int INVALID_REQUEST = -32600;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;
    private static final int INTERNAL_ERROR = -32603;

    private final ServiceDirectory serviceDirectory;
    private final McpToolInvoker mcpToolInvoker;
    private final JsonMapper jsonMapper;

    /**
     * Handles one JSON-RPC request body for the given authenticated participant.
     * @param body the raw request body
     * @param participant the authenticated caller
     * @return a future completing with the JSON-RPC response, or with null when the request was a
     *         notification and no response body must be sent
     */
    public CompletableFuture<JsonRpcResponse> handle(String body, Participant participant) {
        JsonNode parsed;
        try {
            parsed = jsonMapper.readTree(body);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(JsonRpcResponse.error(null, PARSE_ERROR, "Parse error"));
        }

        CompletableFuture<JsonRpcResponse> ret;
        if (parsed.isArray()) {
            // JSON-RPC batching was removed from the MCP spec
            ret = CompletableFuture.completedFuture(JsonRpcResponse.error(null, INVALID_REQUEST, "Batch requests are not supported"));
        } else if (!parsed.isObject()) {
            ret = CompletableFuture.completedFuture(JsonRpcResponse.error(null, INVALID_REQUEST, "Invalid Request"));
        } else {
            JsonRpcRequest request = jsonMapper.treeToValue(parsed, JsonRpcRequest.class);
            if (request.getMethod() == null) {
                ret = CompletableFuture.completedFuture(JsonRpcResponse.error(request.getId(), INVALID_REQUEST, "Invalid Request"));
            } else {
                JsonNode id = request.getId();
                ObjectNode params = request.getParams() != null ? request.getParams() : jsonMapper.createObjectNode();
                ret = switch (request.getMethod()) {
                    case "initialize" -> CompletableFuture.completedFuture(JsonRpcResponse.result(id, initialize(params)));
                    case "notifications/initialized" -> CompletableFuture.completedFuture(null);
                    case "ping" -> CompletableFuture.completedFuture(JsonRpcResponse.result(id, Map.of()));
                    case "tools/list" -> toolsList(id, params, participant);
                    case "tools/call" -> toolsCall(id, params, participant);
                    default -> CompletableFuture.completedFuture(
                            JsonRpcResponse.error(id, METHOD_NOT_FOUND, "Method not found: " + request.getMethod()));
                };
            }
        }
        return ret;
    }

    private McpInitializeResult initialize(ObjectNode params) {
        String requested = params.path("protocolVersion").asString(null);
        String version = McpJsonRpcHandler.class.getPackage().getImplementationVersion();
        // a supported requested version is echoed; anything else negotiates down to our latest
        return new McpInitializeResult()
                .setProtocolVersion(SUPPORTED_PROTOCOL_VERSIONS.contains(requested) ? requested : LATEST_PROTOCOL_VERSION)
                .setCapabilities(Map.of("tools", Map.of("listChanged", false)))
                .setServerInfo(new McpServerInfo().setName("kinotic-api-gateway")
                                                  .setVersion(version != null ? version : "unknown"));
    }

    private CompletableFuture<JsonRpcResponse> toolsList(JsonNode id, ObjectNode params, Participant participant) {
        McpCallerScope scope = McpCallerScope.from(participant);
        String cursor = params.path("cursor").isString() ? params.get("cursor").asString() : null;
        // cursor paging per the MCP pagination spec; the id sort keys the search_after the cursor encodes
        Pageable pageable = Pageable.create(cursor, TOOL_LIST_PAGE_SIZE, Sort.by("id"));
        CompletableFuture<Page<McpToolDefinition>> query;
        try {
            query = serviceDirectory.findMcpToolsCallableBy(scope.organizationId(), scope.applicationId(), pageable);
        } catch (Exception e) {
            // an unreadable cursor fails before the search runs; the spec maps invalid cursors to -32602
            return CompletableFuture.completedFuture(JsonRpcResponse.error(id, INVALID_PARAMS, "Invalid cursor"));
        }
        return query.thenApply(page -> {
            List<McpToolListing> tools = new ArrayList<>(page.getContent().size());
            for (McpToolDefinition tool : page.getContent()) {
                tools.add(new McpToolListing()
                                  .setName(tool.getToolName())
                                  .setDescription(tool.getDescription())
                                  // stored at write time as a JSON string, embedded here as the schema object
                                  .setInputSchema(jsonMapper.readTree(tool.getInputSchema()))
                                  .setAnnotations(new McpToolAnnotations()
                                                          .setReadOnlyHint(tool.isReadOnlyHint())
                                                          .setDestructiveHint(tool.isDestructiveHint())
                                                          .setIdempotentHint(tool.isIdempotentHint())));
            }
            McpToolsListResult listResult = new McpToolsListResult().setTools(tools);
            if (page instanceof CursorPage<McpToolDefinition> cursorPage) {
                listResult.setNextCursor(cursorPage.getCursor());
            }
            return JsonRpcResponse.result(id, listResult);
        });
    }

    private CompletableFuture<JsonRpcResponse> toolsCall(JsonNode id, ObjectNode params, Participant participant) {
        CompletableFuture<JsonRpcResponse> ret;
        if (!params.path("name").isString()) {
            ret = CompletableFuture.completedFuture(JsonRpcResponse.error(id, INVALID_PARAMS, "tools/call requires a tool name"));
        } else {
            ObjectNode arguments = params.get("arguments") instanceof ObjectNode argumentsNode
                    ? argumentsNode
                    : jsonMapper.createObjectNode();
            ret = mcpToolInvoker.invoke(params.get("name").asString(), arguments, participant)
                                .thenApply(toolResult -> JsonRpcResponse.result(id, toolResult))
                                .exceptionally(throwable -> {
                                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                                    JsonRpcResponse response;
                                    if (cause instanceof IllegalArgumentException) {
                                        response = JsonRpcResponse.error(id, INVALID_PARAMS, cause.getMessage());
                                    } else {
                                        log.error("tools/call failed", cause);
                                        response = JsonRpcResponse.error(id, INTERNAL_ERROR, "Internal error");
                                    }
                                    return response;
                                });
        }
        return ret;
    }
}
