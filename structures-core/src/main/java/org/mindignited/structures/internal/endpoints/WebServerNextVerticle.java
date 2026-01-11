package org.mindignited.structures.internal.endpoints;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import lombok.RequiredArgsConstructor;
import org.mindignited.structures.api.config.StructuresProperties;
import org.mindignited.structures.auth.api.config.OidcSecurityServiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web server verticle for serving the modern frontend (structures-frontend-next).
 * Integrates with FrontendConfigurationService to provide dynamic configuration.
 */
@RequiredArgsConstructor
public class WebServerNextVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(WebServerNextVerticle.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final HealthChecks healthChecks;
    private final StructuresProperties properties;
    private final OidcSecurityServiceProperties oidcSecurityServiceProperties;
    private HttpServer server;

    @Override
    public void start(Promise<Void> startPromise) {
        server = vertx.createHttpServer();

        Router router = Router.router(vertx);

        String allowedOriginPattern = properties.getCorsAllowedOriginPattern();
        if ("*".equals(allowedOriginPattern)) {
            allowedOriginPattern = ".*";
        }

        CorsHandler corsHandler = CorsHandler.create()
                                             .addRelativeOrigin(allowedOriginPattern)
                                             .allowedHeaders(properties.getCorsAllowedHeaders());
        if(properties.getCorsAllowCredentials() != null){
            corsHandler.allowCredentials(properties.getCorsAllowCredentials());
        }

        Route route = router.route().handler(corsHandler);

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
        server.requestHandler(router)
              .listen(properties.getWebServerPort(), ar -> {
                  if (ar.succeeded()) {
                      startPromise.complete();
                  } else {
                      startPromise.fail(ar.cause());
                  }
              });
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

    @Override
    public void stop(Promise<Void> stopPromise) {
        server.close(stopPromise);
    }
}
