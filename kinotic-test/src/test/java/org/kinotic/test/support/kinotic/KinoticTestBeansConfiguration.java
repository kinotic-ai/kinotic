package org.kinotic.test.support.kinotic;

import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthChecks;
import org.kinotic.domain.api.ee.services.OwnedIdentityProvider;
import org.kinotic.ee.internal.api.services.security.EntraIdentityProvider;
import org.kinotic.ee.internal.api.services.security.OktaIdentityProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies infrastructure beans the kinotic-test context needs but that are normally
 * owned by the api-gateway module, which this module does not depend on. Mirrors the
 * {@code HealthChecks} bean from {@code ApiGatewayConfiguration} so domain can register
 * its elasticsearch health check. Also registers the Okta and Microsoft Entra identity providers, which a server
 * registers only under a Kinotic Enterprise License key, so the OIDC configuration tests can save configurations of
 * both.
 */
@Configuration
public class KinoticTestBeansConfiguration {

    @Bean
    public HealthChecks healthChecks(Vertx vertx) {
        return HealthChecks.create(vertx);
    }

    @Bean
    public OwnedIdentityProvider entraIdentityProvider() {
        return new EntraIdentityProvider();
    }

    @Bean
    public OwnedIdentityProvider oktaIdentityProvider() {
        return new OktaIdentityProvider();
    }
}
