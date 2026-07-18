package org.kinotic.persistence.internal.endpoints;

import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.core.api.security.SecurityService;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.persistence.api.config.PersistenceProperties;
import org.kinotic.persistence.internal.endpoints.graphql.DelegatingGqlHandler;
import org.kinotic.persistence.internal.endpoints.graphql.GqlVerticle;
import org.kinotic.persistence.internal.endpoints.openapi.OpenApiVerticle;
import org.kinotic.persistence.internal.endpoints.openapi.OpenApiVertxRouterFactory;
import org.springframework.stereotype.Component;

/**
 * Creates all necessary verticles at runtime so multiple instances can be used
 * TODO: remove when we no longer need as a reference.
 * Created By navidmitchell 🤯on 3/6/24
 */
@RequiredArgsConstructor
public class PersistenceVerticleFactory {

    // Common Deps
    private final SecurityContext securityContext;
    private final PersistenceProperties properties;
    private final SecurityService securityService;

    // Open Api Deps
    private final OpenApiVertxRouterFactory openApiVertxRouterFactory;

    // Gql Deps
    private final DelegatingGqlHandler delegatingGqlHandler;


//    public GqlVerticle createGqlVerticle(){
//        return new GqlVerticle(delegatingGqlHandler, properties, kinoticDomainProperties.getDomain().getSsl(), kinoticDomainProperties.getCors(), securityService, securityContext);
//    }
//
//    public OpenApiVerticle createOpenApiVerticle(){
//        return new OpenApiVerticle(properties, kinoticDomainProperties.getDomain().getSsl(), openApiVertxRouterFactory.createRouter());
//    }
}
