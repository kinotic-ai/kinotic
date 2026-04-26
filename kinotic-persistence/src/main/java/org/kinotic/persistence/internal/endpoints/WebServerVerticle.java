package org.kinotic.persistence.internal.endpoints;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.healthchecks.HealthCheckHandler;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.config.CorsProperties;
import org.kinotic.core.api.config.SslHelper;
import org.kinotic.core.api.config.SslProperties;
import org.kinotic.core.internal.utils.CorsUtil;
import org.kinotic.persistence.api.config.PersistenceProperties;
import org.kinotic.core.api.config.OidcSecurityServiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * Web server verticle for serving the frontend (kinotic-frontend).
 * Integrates with FrontendConfigurationService to provide dynamic configuration.
 */
@RequiredArgsConstructor
public class WebServerVerticle extends VerticleBase {

    private static final Logger logger = LoggerFactory.getLogger(WebServerVerticle.class);

    private final ObjectMapper objectMapper;
    private final HealthChecks healthChecks;
    private final PersistenceProperties properties;
    private final SslProperties sslProperties;
    private final CorsProperties corsProperties;
    private final OidcSecurityServiceProperties oidcSecurityServiceProperties;
    private HttpServer server;


    @Override
    public Future<?> start() throws Exception {
        HttpServerOptions serverOptions = new HttpServerOptions();
        SslHelper.applySsl(serverOptions, sslProperties);
        server = vertx.createHttpServer(serverOptions);

        Router router = Router.router(vertx);

        Route route = router.route().handler(CorsUtil.createCorsHandler(corsProperties));

        HealthCheckHandler healthCheckHandler = HealthCheckHandler.createWithHealthChecks(healthChecks);
        router.get(this.properties.getHealthCheckPath()).handler(healthCheckHandler);

        // Add frontend configuration endpoint if service is available and enabled
        if (oidcSecurityServiceProperties.isEnabled()) {
            String configPath = oidcSecurityServiceProperties.getFrontendConfigurationPath();
            logger.info("Adding frontend configuration endpoint at: {}", configPath);

            router.get(configPath).handler(this::handleFrontendConfiguration);
        }

        if(properties.isEnableStaticFileServer()) {
            route.handler(StaticHandler.create("webroot"));
        }

        // Add SPA fallback - serve index.html for any unmatched routes
        // This ensures client-side routing works on page refresh
        // Must be placed after all other routes
        if(properties.isEnableStaticFileServer()) {
            router.get("/*").handler(ctx -> {
                ctx.response().sendFile("webroot/index.html");
            });
        }

        // Begin listening for requests
        return server.requestHandler(router)
                     .listen(properties.getWebServerPort());
    }

    @Override
    public Future<?> stop() throws Exception {
        return server.close();
    }


    /**
     * Handle requests for frontend configuration.
     * Generates configuration dynamically and returns it as JSON.
     */
    private void handleFrontendConfiguration(RoutingContext context) {
        try {
            // Convert to JSON
            String jsonConfig = objectMapper.writeValueAsString(oidcSecurityServiceProperties);
            
            // Send response
            HttpServerResponse response = context.response();
            response.putHeader("Content-Type", "application/json");
            response.putHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.putHeader("Pragma", "no-cache");
            response.putHeader("Expires", "0");
            response.end(jsonConfig);
            
            logger.debug("Served frontend configuration for request from: {}", context.request().remoteAddress());
            
        } catch (Exception e) {
            logger.error("Failed to generate frontend configuration", e);
            
            // Send error response
            HttpServerResponse response = context.response();
            response.setStatusCode(500);
            response.putHeader("Content-Type", "application/json");
            response.end("{\"error\": \"Failed to generate configuration\"}");
        }
    }

}
