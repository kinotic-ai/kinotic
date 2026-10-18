package org.kinotic.domain.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.services.SiteDomainProvisioner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The {@link SiteDomainProvisioner} of an environment with no edge, {@code kinotic.domain.siteDomain.cloudflare.enabled=false}:
 * logs the subdomain each organization would get and provisions nothing, so sign-up and publishing run
 * where no site is ever served, such as a developer's machine or the test suite.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "kinotic.domain.siteDomain.cloudflare.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingSiteDomainProvisioner implements SiteDomainProvisioner {

    private final KinoticDomainProperties properties;

    @Override
    public Future<Void> ensure(String organizationId) {
        log.info("No edge is configured; organization {} would be served under *.{}.{}",
                 organizationId, organizationId, properties.getDomain().getSiteDomain().getDomain());
        return Future.succeededFuture();
    }
}
