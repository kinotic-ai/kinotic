package org.kinotic.gateway.internal.endpoints;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.stomp.lite.StompServerHandlerFactory;
import io.vertx.ext.stomp.lite.StompServerOptions;
import io.vertx.ext.stomp.lite.StompServerVerticle;
import io.vertx.ext.stomp.lite.StompServerVerticleFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.healthchecks.HealthCheckHandler;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.config.SslHelper;
import org.kinotic.gateway.api.config.ApiGatewayProperties;
import org.kinotic.gateway.api.config.CorsProperties;
import org.kinotic.gateway.api.config.KinoticApiGatewayProperties;
import org.kinotic.gateway.internal.endpoints.rest.LoginHandler;
import org.kinotic.gateway.internal.endpoints.rest.OidcSignupHandler;
import org.kinotic.gateway.internal.endpoints.rest.SignUpHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Provides a factory for creating continuum end point verticles.
 * Created by Navíd Mitchell 🤪 on 3/6/24.
 */
@Component
public class ApiGatewayVertcleFactory {

    private final KinoticProperties kinoticProperties;
    private final KinoticApiGatewayProperties apiGatewayProperties;
    private final ApiGatewayProperties gatewayProperties;
    private final StompServerHandlerFactory stompServerHandlerFactory;
    private final SignUpHandler signUpHandler;
    private final LoginHandler loginHandler;
    private final OidcSignupHandler oidcSignupHandler;
    private final HealthChecks healthChecks;
    private final Vertx vertx;

    public ApiGatewayVertcleFactory(KinoticProperties kinoticProperties,
                                    KinoticApiGatewayProperties kinoticApiGatewayProperties,
                                    StompServerHandlerFactory stompServerHandlerFactory,
                                    SignUpHandler signUpHandler,
                                    LoginHandler loginHandler,
                                    OidcSignupHandler oidcSignupHandler,
                                    HealthChecks healthChecks,
                                    Vertx vertx) {
        this.kinoticProperties = kinoticProperties;
        this.apiGatewayProperties = kinoticApiGatewayProperties;
        this.gatewayProperties = kinoticApiGatewayProperties.getRpcGateway();
        this.stompServerHandlerFactory = stompServerHandlerFactory;
        this.signUpHandler = signUpHandler;
        this.loginHandler = loginHandler;
        this.oidcSignupHandler = oidcSignupHandler;
        this.healthChecks = healthChecks;
        this.vertx = vertx;
    }

    public StompServerVerticle createApiGatewayVerticle(){
        Router router = Router.router(vertx);

        // CORS first — the SPA hits this port from a different origin in prod (Azure
        // Storage → kinotic-server) and from vite (5173) in dev when not proxied.
        router.route().handler(buildCorsHandler(apiGatewayProperties.getCors()));

        // Health check on the api-gateway port so probes work even when the static
        // web-server (9090) is disabled in KinD/Azure.
        router.get(apiGatewayProperties.getHealthCheckPath())
              .handler(HealthCheckHandler.createWithHealthChecks(healthChecks));

        // REST endpoints under /api
        signUpHandler.mountRoutes(router);
        loginHandler.mountRoutes(router);
        oidcSignupHandler.mountRoutes(router);

        StompServerOptions stompServerOptions = gatewayProperties.getStomp();
        // we override the body length with the continuum properties
        stompServerOptions.setMaxBodyLength(kinoticProperties.getMaxEventPayloadSize());
        HttpServerOptions serverOptions = new HttpServerOptions();
        serverOptions.setWebSocketSubProtocols(List.of("v12.stomp"));
        serverOptions.setMaxWebSocketFrameSize(kinoticProperties.getMaxEventPayloadSize());
        SslHelper.applySsl(serverOptions, kinoticProperties.getSsl());

        return StompServerVerticleFactory.create(serverOptions, stompServerOptions, stompServerHandlerFactory, router);
    }

    public WebServerVerticle createWebServerVerticle(){
        return new WebServerVerticle(apiGatewayProperties.getWebServer(), kinoticProperties.getSsl());
    }

    private static CorsHandler buildCorsHandler(CorsProperties cors) {
        String pattern = "*".equals(cors.getAllowedOriginPattern()) ? ".*" : cors.getAllowedOriginPattern();
        CorsHandler handler = CorsHandler.create()
                                         .addOriginWithRegex(pattern)
                                         .allowedHeaders(cors.getAllowedHeaders());
        if (cors.getAllowCredentials() != null) {
            handler.allowCredentials(cors.getAllowCredentials());
        }
        return handler;
    }
}
