package org.kinotic.domain.api.rest;

import io.vertx.core.Future;
import io.vertx.core.net.HostAndPort;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.model.AppHost;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.internal.api.repositories.ApplicationRepository;

/**
 * The {@link ServerSurface} of the app server, which serves every application at an API host of its own: a
 * request is presented the URLs of the application whose API host it was addressed to, and a request addressed
 * to any other host fails with {@code 404}. The application's API host is both its API base URL and its OAuth
 * issuer, and its UI is its primary UI.
 */
@RequiredArgsConstructor
public class AppServerSurface implements ServerSurface {

    private final KinoticDomainProperties domainProperties;
    private final ApplicationRepository applicationRepository;

    /**
     * The application whose API host the request was addressed to. Fails the request with {@code 404} when it
     * was addressed to a host that is no application's API host.
     */
    public AppHost appHost(RoutingContext ctx) {
        AppHost ret = findAppHost(ctx);
        if (ret == null) {
            throw new HttpException(404);
        }
        return ret;
    }

    @Override
    public String apiBaseUrl(RoutingContext ctx) {
        return domainProperties.getDomain().resolveAppApiUrl(appHost(ctx));
    }

    @Override
    public String issuerBaseUrl(RoutingContext ctx) {
        return apiBaseUrl(ctx);
    }

    /**
     * {@inheritDoc} The UI is the application's primary UI, so this fails with {@code 404} when the application
     * does not exist, and with {@code 400} while it has no primary UI.
     */
    @Override
    public Future<String> uiUrl(RoutingContext ctx, String path) {
        AppHost appHost = findAppHost(ctx);
        Future<Application> application = appHost != null
                ? applicationRepository.findById(appHost.applicationId(), appHost.organizationId())
                : Future.succeededFuture();
        return application.map(found -> {
            if (found == null) {
                throw new HttpException(404);
            }
            Validate.isTrue(found.getPrimaryUiUrl() != null, "The application '%s' has no primary UI", found.getId());
            return found.getPrimaryUiUrl() + path;
        });
    }

    // null when the request was addressed to a host that is no application's API host
    private AppHost findAppHost(RoutingContext ctx) {
        HostAndPort authority = ctx.request().authority();
        return authority != null ? domainProperties.getDomain().resolveAppHost(authority.host()) : null;
    }
}
