package org.kinotic.gateway.internal.config;

import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.web.sstore.SessionStore;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.gateway.api.config.ApiGatewayProperties;
import org.kinotic.gateway.api.config.KinoticApiGatewayProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

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

    @Bean
    public SessionStore sessionStore(Vertx vertx, JsonMapper jsonMapper){
        // ConnectedInfo rides in the web session and is marshalled by this store when clustered.
        // Vert.x rebuilds it reflectively on read, so it can't be injected — hand it the
        // participant-aware mapper once here, before any session is written.
        ConnectedInfo.setSerializationMapper(jsonMapper);
        return SessionStore.create(vertx);
    }
}
