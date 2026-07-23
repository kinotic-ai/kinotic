package org.kinotic.gateway.internal.mcp;

import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.security.SecurityService;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Mounts the stateless MCP endpoint: JSON-RPC 2.0 over {@code POST /mcp}, every request independently
 * authenticated from its headers. There are no sessions and no server-initiated streams, so all other verbs
 * are rejected.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kinotic.disableMcp", havingValue = "false", matchIfMissing = true)
public class McpGatewayRoutes implements SuppliesGatewayRoutes {

    private static final String MCP_ROUTE = "/mcp";
    private static final int MAX_BODY_SIZE = 262144;

    private final SecurityService securityService;
    private final McpJsonRpcHandler mcpJsonRpcHandler;
    private final JsonMapper jsonMapper;

    @Override
    public void mountRoutes(Router router) {
        router.post(MCP_ROUTE).handler(BodyHandler.create().setBodyLimit(MAX_BODY_SIZE));
        router.post(MCP_ROUTE).handler(this::handlePost);
        router.route(MCP_ROUTE).handler(ctx -> ctx.response().setStatusCode(405).end());
    }

    private void handlePost(RoutingContext ctx) {
        Map<String, String> authenticationInfo = new HashMap<>();
        ctx.request().headers().forEach(entry -> authenticationInfo.put(entry.getKey().toLowerCase(), entry.getValue()));

        Future.fromCompletionStage(securityService.authenticate(authenticationInfo))
              .onSuccess(participant -> Future.fromCompletionStage(mcpJsonRpcHandler.handle(ctx.body().asString(), participant))
                                              .onSuccess(response -> {
                                                  if (response == null) {
                                                      // a notification gets no response body
                                                      ctx.response().setStatusCode(202).end();
                                                  } else {
                                                      ctx.response()
                                                         .putHeader("Content-Type", "application/json")
                                                         .end(jsonMapper.writeValueAsString(response));
                                                  }
                                              })
                                              .onFailure(throwable -> {
                                                  log.error("MCP request handling failed", throwable);
                                                  ctx.response().setStatusCode(500).end();
                                              }))
              .onFailure(throwable -> ctx.response().setStatusCode(401).end());
    }
}
