package org.kinotic.gateway.internal.endpoints;

import io.vertx.core.Vertx;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.stomp.lite.StompServerHandlerFactory;
import io.vertx.ext.stomp.lite.StompServerOptions;
import io.vertx.ext.stomp.lite.StompServerVerticle;
import io.vertx.ext.stomp.lite.StompServerVerticleFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.healthchecks.HealthCheckHandler;
import io.vertx.ext.web.sstore.SessionStore;
import lombok.RequiredArgsConstructor;
import org.kinotic.gateway.api.utils.ApiGatewayUtil;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import org.kinotic.gateway.api.config.KinoticApiGatewayProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Provides a factory for creating continuum end point verticles.
 * Created by Navíd Mitchell 🤪 on 3/6/24.
 */
@Component
@RequiredArgsConstructor
public class ApiGatewayVertcleFactory {

    private final KinoticApiGatewayProperties properties;
    private final StompServerHandlerFactory stompServerHandlerFactory;
    private final List<SuppliesGatewayRoutes> gatewayRoutes;
    private final HealthChecks healthChecks;
    private final Vertx vertx;
    private final SessionStore sessionStore;

    public StompServerVerticle createApiGatewayVerticle(){
        Router router = Router.router(vertx);

        // CORS first — the SPA is a different origin from this gateway (portal.kinotic.ai
        // vs api.kinotic.ai in prod, vite's :5173 in dev). They're same-site, so the
        // SameSite=Lax session cookie flows; credentialed CORS (kinotic.apiGateway.cors.*) lets the
        // cross-origin login fetch store it. Shared with the openapi/graphql routes.
        router.route().handler(ApiGatewayUtil.createCorsHandler(properties.getApiGateway().getCors()));

        // Health check on the api-gateway port so probes work even when the static
        // web-server (9090) is disabled in KinD/Azure.
        router.get("/health")
              .handler(HealthCheckHandler.createWithHealthChecks(healthChecks));

        // Add body handler for all api paths
        router.route("/api/*").handler(BodyHandler.create().setBodyLimit(16384));

        // Add session handler to all api paths
        SessionHandler sessionHandler = SessionHandler.create(sessionStore)
                      .setCookieHttpOnlyFlag(true)
                      .setCookieSecureFlag(true)
                      .setCookieSameSite(CookieSameSite.LAX)
                      .setSessionTimeout(properties.getApiGateway().getSessionTimeout())
                      .setLazySession(true);

        router.route("/api/*").handler(sessionHandler);

        // REST endpoints under /api — every bean supplying gateway routes is collected and mounted
        // here, so a disabled module contributes nothing and the gateway still boots.
        gatewayRoutes.forEach(routes -> routes.mountRoutes(router));

        StompServerOptions stompServerOptions = properties.getApiGateway().getStomp();
        // we override the body length with the continuum properties
        stompServerOptions.setMaxBodyLength(properties.getMaxEventPayloadSize());

        // The STOMP WebSocket handshake authenticates from the browser session, so the
        // SessionHandler must also cover the WebSocket path — it is not under /api/*.
        router.route(stompServerOptions.getWebsocketPath()).handler(sessionHandler);

        HttpServerOptions serverOptions = new HttpServerOptions();
        serverOptions.setWebSocketSubProtocols(List.of("v12.stomp"));
        serverOptions.setMaxWebSocketFrameSize(properties.getMaxEventPayloadSize());
        ApiGatewayUtil.applySsl(serverOptions, properties.getApiGateway().getSsl());

        return StompServerVerticleFactory.create(serverOptions, stompServerOptions, stompServerHandlerFactory, router);
    }

    public WebServerVerticle createWebServerVerticle(){
        return new WebServerVerticle(properties.getApiGateway().getWebServer(), properties.getApiGateway().getSsl());
    }
}
