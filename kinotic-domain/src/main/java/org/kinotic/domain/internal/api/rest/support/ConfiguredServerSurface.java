package org.kinotic.domain.internal.api.rest.support;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.rest.ServerSurface;

/**
 * The {@link ServerSurface} of a server whose URLs are fixed by configuration: {@code kinotic.domain.apiBaseUrl},
 * {@code kinotic.domain.oauth.issuerBaseUrl} and {@code kinotic.domain.appBaseUrl}, with their fallbacks.
 */
@RequiredArgsConstructor
public class ConfiguredServerSurface implements ServerSurface {

    private final KinoticDomainProperties domainProperties;

    @Override
    public String apiBaseUrl(RoutingContext ctx) {
        return domainProperties.getDomain().resolveApiBaseUrl();
    }

    @Override
    public String issuerBaseUrl(RoutingContext ctx) {
        return domainProperties.getDomain().resolveIssuerBaseUrl();
    }

    @Override
    public Future<String> uiUrl(RoutingContext ctx, String path) {
        return Future.succeededFuture(domainProperties.getDomain().getAppBaseUrl() + path);
    }
}
