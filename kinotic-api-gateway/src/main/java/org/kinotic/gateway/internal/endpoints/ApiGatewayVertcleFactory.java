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
import org.kinotic.core.api.config.SslHelper;
import org.kinotic.core.internal.utils.CorsUtil;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.gateway.api.config.KinoticApiGatewayProperties;
import org.kinotic.billing.api.rest.BillingGatewayRoutes;
import org.kinotic.gateway.internal.endpoints.rest.*;
import org.kinotic.github.api.rest.GitHubGatewayRoutes;
import org.springframework.beans.factory.ObjectProvider;
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
    private final KinoticDomainProperties domainProperties;
    private final StompServerHandlerFactory stompServerHandlerFactory;
    private final SignUpHandler signUpHandler;
    private final OrganizationLoginHandler organizationLoginHandler;
    private final OrganizationSignupHandler organizationSignupHandler;
    private final ApplicationLoginHandler applicationLoginHandler;
    private final SystemLoginHandler systemLoginHandler;
    private final CliDeviceLoginHandler cliDeviceLoginHandler;
    private final LogoutHandler logoutHandler;
    private final GitHubGatewayRoutes githubGatewayRoutes;
    // ObjectProvider: the billing module is conditional on kinotic.billing.enabled
    private final ObjectProvider<BillingGatewayRoutes> billingGatewayRoutes;
    private final HealthChecks healthChecks;
    private final Vertx vertx;
    private final SessionStore sessionStore;

    public StompServerVerticle createApiGatewayVerticle(){
        Router router = Router.router(vertx);

        // CORS first — the SPA is a different origin from this gateway (portal.kinotic.ai
        // vs api.kinotic.ai in prod, vite's :5173 in dev). They're same-site, so the
        // SameSite=Lax session cookie flows; credentialed CORS (kinotic.cors.*) lets the
        // cross-origin login fetch store it. Shared with the openapi/graphql routes.
        router.route().handler(CorsUtil.createCorsHandler(properties.getCors()));

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

        // REST endpoints under /api
        signUpHandler.mountRoutes(router);
        organizationLoginHandler.mountRoutes(router);
        organizationSignupHandler.mountRoutes(router);
        applicationLoginHandler.mountRoutes(router);
        cliDeviceLoginHandler.mountRoutes(router);
        logoutHandler.mountRoutes(router);
        //systemLoginHandler.mountRoutes(router);
        githubGatewayRoutes.mountRoutes(router);
        billingGatewayRoutes.ifAvailable(routes -> routes.mountRoutes(router));

        StompServerOptions stompServerOptions = properties.getApiGateway().getStomp();
        // we override the body length with the continuum properties
        stompServerOptions.setMaxBodyLength(properties.getMaxEventPayloadSize());

        // The STOMP WebSocket handshake authenticates from the browser session, so the
        // SessionHandler must also cover the WebSocket path — it is not under /api/*.
        router.route(stompServerOptions.getWebsocketPath()).handler(sessionHandler);

        HttpServerOptions serverOptions = new HttpServerOptions();
        serverOptions.setWebSocketSubProtocols(List.of("v12.stomp"));
        serverOptions.setMaxWebSocketFrameSize(properties.getMaxEventPayloadSize());
        SslHelper.applySsl(serverOptions, domainProperties.getDomain().getSsl());

        return StompServerVerticleFactory.create(serverOptions, stompServerOptions, stompServerHandlerFactory, router);
    }

    public WebServerVerticle createWebServerVerticle(){
        return new WebServerVerticle(properties.getApiGateway().getWebServer(), domainProperties.getDomain().getSsl());
    }
}
