package org.kinotic.persistence.internal.endpoints;

import io.vertx.ext.healthchecks.HealthChecks;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.config.OidcSecurityServiceProperties;
import org.kinotic.persistence.api.config.PersistenceProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Creates all needed verticles at runtime so multiple instances can be used
 * Created By navidmitchell 🤯on 3/6/24
 */
@Component
@RequiredArgsConstructor
public class PersistenceVerticleFactory {

    // Common Deps
    private final KinoticProperties kinoticProperties;
    private final PersistenceProperties properties;

    // Web Server Deps
    private final ObjectMapper objectMapper;
    private final HealthChecks healthChecks;
    private final OidcSecurityServiceProperties oidcSecurityServiceProperties;


    public WebServerVerticle createWebServerNextVerticle(){
        return new WebServerVerticle(objectMapper, healthChecks, properties, kinoticProperties.getSsl(), oidcSecurityServiceProperties);
    }
}
