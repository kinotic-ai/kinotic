package org.kinotic.gateway.internal.config;

import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthChecks;
import org.kinotic.gateway.api.config.ApiGatewayProperties;
import org.kinotic.gateway.api.config.KinoticApiGatewayProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring beans owned by the api-gateway module. {@link HealthChecks} is exposed here so
 * other modules (e.g. kinotic-persistence registering its elasticsearch check) can inject
 * it without taking a compile dependency on a higher-level web-tier module.
 */
@Configuration
public class ApiGatewayConfiguration {

    @Bean
    public HealthChecks healthChecks(Vertx vertx) {
        return HealthChecks.create(vertx);
    }

    @Bean
    public ApiGatewayProperties rpcGatewayProperties(KinoticApiGatewayProperties kinoticProperties){
        return kinoticProperties.getApiGateway();
    }
}
