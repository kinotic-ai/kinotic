package org.kinotic.test.support.kinotic;

import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthChecks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies infrastructure beans the kinotic-test context needs but that are normally
 * owned by the api-gateway module, which this module does not depend on. Mirrors the
 * {@code HealthChecks} bean from {@code ApiGatewayConfiguration} so persistence can
 * register its elasticsearch health check.
 */
@Configuration
public class KinoticTestBeansConfiguration {

    @Bean
    public HealthChecks healthChecks(Vertx vertx) {
        return HealthChecks.create(vertx);
    }
}
