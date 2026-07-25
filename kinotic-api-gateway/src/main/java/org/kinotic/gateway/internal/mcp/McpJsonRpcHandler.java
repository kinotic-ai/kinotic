package org.kinotic.gateway.internal.mcp;

import io.vertx.core.Future;
import io.vertx.core.json.DecodeException;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.CursorPage;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.core.api.directory.McpToolDefinition;
import org.kinotic.core.api.directory.ServiceDirectory;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityService;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import org.kinotic.gateway.internal.mcp.model.JsonRpcRequest;
import org.kinotic.gateway.internal.mcp.model.JsonRpcResponse;
import org.kinotic.gateway.internal.mcp.model.McpInitializeResult;
import org.kinotic.gateway.internal.mcp.model.McpServerInfo;
import org.kinotic.gateway.internal.mcp.model.McpToolAnnotations;
import org.kinotic.gateway.internal.mcp.model.McpToolListing;
import org.kinotic.gateway.internal.mcp.model.McpToolsListResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The stateless MCP server: mounts {@code POST /mcp} on the gateway router, authenticates every request
 * independently from its headers, and dispatches the JSON-RPC 2.0 request to the MCP methods the gateway
 * supports. There are no sessions and no server-initiated messages, so all other verbs are rejected.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kinotic.disableMcp", havingValue = "false", matchIfMissing = true)
public class McpJsonRpcHandler implements SuppliesGatewayRoutes {

    private static final String MCP_ROUTE = "/mcp";
    private static final int MAX_BODY_SIZE = 262144;

    private static final String LATEST_PROTOCOL_VERSION = "2025-11-25";
    private static final Set<String> SUPPORTED_PROTOCOL_VERSIONS = Set.of("2025-03-26", "2025-06-18", LATEST_PROTOCOL_VERSION);
    private static final int TOOL_LIST_PAGE_SIZE = 1000;

    private static final int PARSE_ERROR = -32700;
    private static final int INVALID_REQUEST = -32600;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;
    private static final int INTERNAL_ERROR = -32603;

    private final SecurityService securityService;
    private final ServiceDirectory serviceDirectory;
    private final McpToolInvoker mcpToolInvoker;
    private final JsonMapper jsonMapper;

    @Override
    public void mountRoutes(Router router) {
        router.post(MCP_ROUTE).handler(BodyHandler.create().setBodyLimit(MAX_BODY_SIZE));
        router.post(MCP_ROUTE).handler(this::handlePost);
        router.route(MCP_ROUTE).handler(ctx -> ctx.response().setStatusCode(405).end());
    }

    private void handlePost(RoutingContext ctx) {
        Map<String, String> authenticationInfo = new HashMap<>();
        ctx.request()
           .headers()
           .forEach(entry -> authenticationInfo.put(entry.getKey().toLowerCase(), entry.getValue()));

        securityService.authenticate(authenticationInfo)
                       .onSuccess(participant -> handle(ctx.body(), participant)
                               .onSuccess(response -> {
                                   if (response == null) {
                                       // a notification gets no response body
                                       ctx.response().setStatusCode(202).end();
                                   } else {
                                       ctx.json(response);
                                   }
                               })
                               .onFailure(throwable -> {
                                   log.error("MCP request handling failed", throwable);
                                   ctx.response().setStatusCode(500).end();
                               }))
                       .onFailure(_ -> ctx.response().setStatusCode(401).end());
    }

    // succeeds with null when the request was a notification and no response body must be sent
    private Future<JsonRpcResponse> handle(RequestBody body, Participant participant) {
        JsonRpcRequest request;
        try {

            request = body.asPojo(JsonRpcRequest.class);

        } catch (DecodeException e) {
            // a StreamReadException cause is malformed JSON; anything else is valid JSON of the
            // wrong shape, including the batch form the MCP spec removed
            return Future.succeededFuture(e.getCause() instanceof StreamReadException
                                                  ? JsonRpcResponse.error(null, PARSE_ERROR, "Parse error")
                                                  : JsonRpcResponse.error(null, INVALID_REQUEST, "Invalid Request"));
        }

        Future<JsonRpcResponse> ret;

        if (request != null && request.getMethod() != null) {

            Object id = request.getId();
            ObjectNode params = request.getParams() != null ? request.getParams() : jsonMapper.createObjectNode();
            ret = switch (request.getMethod()) {
                case "initialize" -> Future.succeededFuture(JsonRpcResponse.result(id, initialize(params)));
                case "notifications/initialized" -> Future.succeededFuture(null);
                case "ping" -> Future.succeededFuture(JsonRpcResponse.result(id, Map.of()));
                case "tools/list" -> toolsList(id, params, participant);
                case "tools/call" -> toolsCall(id, params, participant);
                default -> Future.succeededFuture(
                        JsonRpcResponse.error(id, METHOD_NOT_FOUND, "Method not found: " + request.getMethod()));
            };

        } else {
            ret = Future.succeededFuture(JsonRpcResponse.error(request != null ? request.getId() : null, INVALID_REQUEST,
                                                               "Invalid Request"));
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

    private Future<JsonRpcResponse> toolsList(Object id, ObjectNode params, Participant participant) {

        McpCallerScope scope = McpCallerScope.from(participant);
        String cursor = params.path("cursor").isString() ? params.get("cursor").asString() : null;
        Pageable pageable = Pageable.create(cursor, TOOL_LIST_PAGE_SIZE, Sort.by("id"));

        try {
            Future<Page<McpToolDefinition>> query = serviceDirectory.findMcpToolsCallableBy(scope.organizationId(),
                                                                                            scope.applicationId(),
                                                                                            pageable);

            return query.map(page -> {

                List<McpToolListing> tools = new ArrayList<>(page.getContent().size());
                for (McpToolDefinition tool : page.getContent()) {
                    tools.add(new McpToolListing()
                                      .setName(tool.getName())
                                      .setTitle(tool.getTitle())
                                      .setDescription(tool.getDescription())
                                      .setInputSchema(tool.getInputSchema())
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

        } catch (Exception e) {
            // an unreadable cursor fails before the search runs; the spec maps invalid cursors to -32602
            return Future.succeededFuture(JsonRpcResponse.error(id, INVALID_PARAMS, "Invalid cursor"));
        }
    }

    private Future<JsonRpcResponse> toolsCall(Object id, ObjectNode params, Participant participant) {

        Future<JsonRpcResponse> ret;
        if (params.path("name").isString()) {

            ObjectNode arguments = params.get("arguments") instanceof ObjectNode argumentsNode
                    ? argumentsNode
                    : jsonMapper.createObjectNode();

            ret = mcpToolInvoker.invoke(params.get("name").asString(), arguments, participant)
                                .map(toolResult -> JsonRpcResponse.result(id, toolResult))
                                .otherwise(throwable -> {
                                    // a repository failure crosses the strategy's fromCompletionStage wrapped in CompletionException
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
        } else {
            ret = Future.succeededFuture(JsonRpcResponse.error(id, INVALID_PARAMS, "tools/call requires a tool name"));
        }
        return ret;
    }
}
