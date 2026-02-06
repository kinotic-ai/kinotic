package org.kinotic.structures.internal.endpoints;

import io.vertx.ext.healthchecks.HealthChecks;
import lombok.RequiredArgsConstructor;
import org.kinotic.continuum.api.security.SecurityService;
import org.kinotic.structures.api.config.StructuresProperties;
import org.kinotic.structures.auth.api.config.OidcSecurityServiceProperties;
import org.kinotic.structures.internal.endpoints.graphql.DelegatingGqlHandler;
import org.kinotic.structures.internal.endpoints.graphql.GqlVerticle;
import org.kinotic.structures.internal.endpoints.openapi.OpenApiVerticle;
import org.kinotic.structures.internal.endpoints.openapi.OpenApiVertxRouterFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Creates all needed verticles at runtime so multiple instances can be used
 * Created By navidmitchell 🤯on 3/6/24
 */
@Component
@RequiredArgsConstructor
public class StructuresVerticleFactory {

    // Common Deps
    private final StructuresProperties properties;
    private final SecurityService securityService;

    // Open Api Deps
    private final OpenApiVertxRouterFactory openApiVertxRouterFactory;

    // Gql Deps
    private final DelegatingGqlHandler delegatingGqlHandler;

    // Web Server Deps
    private final ObjectMapper objectMapper;
    private final HealthChecks healthChecks;
    private final OidcSecurityServiceProperties oidcSecurityServiceProperties;

    
    public GqlVerticle createGqlVerticle(){
        return new GqlVerticle(delegatingGqlHandler, properties, securityService);
    }

    public OpenApiVerticle createOpenApiVerticle(){
        return new OpenApiVerticle(properties, openApiVertxRouterFactory.createRouter());
    }

    public WebServerNextVerticle createWebServerNextVerticle(){
        return new WebServerNextVerticle(objectMapper, healthChecks, properties, oidcSecurityServiceProperties);
    }
}
