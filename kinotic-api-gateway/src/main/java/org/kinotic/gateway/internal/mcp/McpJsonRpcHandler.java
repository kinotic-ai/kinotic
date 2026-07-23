package org.kinotic.gateway.internal.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.security.Participant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

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
     * @return a future completing with the JSON-RPC response node, or with null when the request was a
     *         notification and no response body must be sent
     */
    public CompletableFuture<ObjectNode> handle(String body, Participant participant) {
        JsonNode request;
        try {
            request = jsonMapper.readTree(body);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(error(NullNode.getInstance(), PARSE_ERROR, "Parse error"));
        }

        CompletableFuture<ObjectNode> ret;
        if (request.isArray()) {
            // JSON-RPC batching was removed from the MCP spec
            ret = CompletableFuture.completedFuture(error(NullNode.getInstance(), INVALID_REQUEST, "Batch requests are not supported"));
        } else if (!request.isObject() || !request.path("method").isString()) {
            ret = CompletableFuture.completedFuture(error(NullNode.getInstance(), INVALID_REQUEST, "Invalid Request"));
        } else {
            JsonNode id = request.has("id") ? request.get("id") : null;
            String method = request.path("method").asString();
            ObjectNode params = request.get("params") instanceof ObjectNode paramsNode
                    ? paramsNode
                    : jsonMapper.createObjectNode();
            ret = switch (method) {
                case "initialize" -> CompletableFuture.completedFuture(result(id, initialize(params)));
                case "notifications/initialized" -> CompletableFuture.completedFuture(null);
                case "ping" -> CompletableFuture.completedFuture(result(id, jsonMapper.createObjectNode()));
                case "tools/list" -> toolsList(id, participant);
                case "tools/call" -> toolsCall(id, params, participant);
                default -> CompletableFuture.completedFuture(error(id, METHOD_NOT_FOUND, "Method not found: " + method));
            };
        }
        return ret;
    }

    private ObjectNode initialize(ObjectNode params) {
        String requested = params.path("protocolVersion").asString(null);
        ObjectNode ret = jsonMapper.createObjectNode();
        // a supported requested version is echoed; anything else negotiates down to our latest
        ret.put("protocolVersion", SUPPORTED_PROTOCOL_VERSIONS.contains(requested) ? requested : LATEST_PROTOCOL_VERSION);
        ret.putObject("capabilities")
           .putObject("tools")
           .put("listChanged", false);
        ObjectNode serverInfo = ret.putObject("serverInfo");
        serverInfo.put("name", "kinotic-api-gateway");
        String version = McpJsonRpcHandler.class.getPackage().getImplementationVersion();
        serverInfo.put("version", version != null ? version : "unknown");
        return ret;
    }

    private CompletableFuture<ObjectNode> toolsList(JsonNode id, Participant participant) {
        McpCallerScope scope = McpCallerScope.from(participant);
        return serviceDirectory.findMcpToolsCallableBy(scope.organizationId(),
                                                       scope.applicationId(),
                                                       Pageable.create(0, TOOL_LIST_PAGE_SIZE, Sort.unsorted()))
                               .thenApply(page -> {
                                   ObjectNode listResult = jsonMapper.createObjectNode();
                                   ArrayNode tools = listResult.putArray("tools");
                                   for (McpToolDefinition tool : page.getContent()) {
                                       ObjectNode toolNode = tools.addObject();
                                       toolNode.put("name", tool.getToolName());
                                       toolNode.put("description", tool.getDescription());
                                       // stored at write time as a JSON string, embedded here as the schema object
                                       toolNode.set("inputSchema", jsonMapper.readTree(tool.getInputSchema()));
                                       ObjectNode annotations = toolNode.putObject("annotations");
                                       annotations.put("readOnlyHint", tool.isReadOnlyHint());
                                       annotations.put("destructiveHint", tool.isDestructiveHint());
                                       annotations.put("idempotentHint", tool.isIdempotentHint());
                                   }
                                   return result(id, listResult);
                               });
    }

    private CompletableFuture<ObjectNode> toolsCall(JsonNode id, ObjectNode params, Participant participant) {
        CompletableFuture<ObjectNode> ret;
        if (!params.path("name").isString()) {
            ret = CompletableFuture.completedFuture(error(id, INVALID_PARAMS, "tools/call requires a tool name"));
        } else {
            ObjectNode arguments = params.get("arguments") instanceof ObjectNode argumentsNode
                    ? argumentsNode
                    : jsonMapper.createObjectNode();
            ret = mcpToolInvoker.invoke(params.get("name").asString(), arguments, participant)
                                .thenApply(toolResult -> result(id, toolResult))
                                .exceptionally(throwable -> {
                                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                                    ObjectNode response;
                                    if (cause instanceof IllegalArgumentException) {
                                        response = error(id, INVALID_PARAMS, cause.getMessage());
                                    } else {
                                        log.error("tools/call failed", cause);
                                        response = error(id, INTERNAL_ERROR, "Internal error");
                                    }
                                    return response;
                                });
        }
        return ret;
    }

    private ObjectNode result(JsonNode id, ObjectNode result) {
        ObjectNode ret = jsonMapper.createObjectNode();
        ret.put("jsonrpc", "2.0");
        ret.set("id", id == null ? NullNode.getInstance() : id);
        ret.set("result", result);
        return ret;
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode ret = jsonMapper.createObjectNode();
        ret.put("jsonrpc", "2.0");
        ret.set("id", id == null ? NullNode.getInstance() : id);
        ObjectNode error = ret.putObject("error");
        error.put("code", code);
        error.put("message", message);
        return ret;
    }
}
