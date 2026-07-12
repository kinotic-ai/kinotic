package org.kinotic.gateway.internal.config;

import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.web.sstore.ClusteredSessionStore;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.sstore.SessionStore;
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

    private static final long SESSION_RETRY_TIMEOUT_MILLIS = 1000;

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
        // ConnectedInfo is stored in the SessionStore and is marshalled by this store when clustered.
        // Vert.x rebuilds it reflectively on read, so the jsonMapper cannot be injected, so we use a static field.
        ConnectedInfo.setSerializationMapper(jsonMapper);

        if(vertx.isClustered()){
            // SessionHandler retries a session id it cannot find until the store's retryTimeout elapses,
            // to cover a session written on another node that has not propagated yet. With the 5s default,
            // a browser presenting a stale cookie (its session gone, e.g. a restart cleared the Ignite map)
            // stalls its first /api request for the full 5 seconds before being treated as session-less.
            // One second still covers the propagation race without a user-visible hang on stale cookies.
            ClusteredSessionStore store = ClusteredSessionStore.create(vertx, SESSION_RETRY_TIMEOUT_MILLIS);
            // The backing cluster-wide map is created lazily on first access; touch it now so the first
            // request after boot does not pay for the Ignite cache creation.
            store.get("noop");
            return store;
        }
        return LocalSessionStore.create(vertx);
    }
}
