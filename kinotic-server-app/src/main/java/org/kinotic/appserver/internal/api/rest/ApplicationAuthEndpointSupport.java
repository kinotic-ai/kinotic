package org.kinotic.appserver.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.core.net.HostAndPort;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import org.apache.commons.lang3.Validate;
import org.kinotic.appserver.api.config.AppServerProperties;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.utils.AppHostUtil;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.services.security.KinoticJwtIssuer;
import org.kinotic.domain.api.services.security.OrgSignupOidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.RefreshTokenService;
import org.springframework.stereotype.Component;

/**
 * The app server's {@link AuthEndpointSupport}. Every request the app server serves is addressed to one
 * application's API host, {@code <organizationId>--<applicationId>} under {@code kinotic.appServer.apiBaseUrl},
 * and {@link #applicationKey} names that application; the browser flows send the user to its primary UI.
 */
@Component
public class ApplicationAuthEndpointSupport extends AuthEndpointSupport {

    private final AppServerProperties properties;
    private final ApplicationRepository applicationRepository;

    public ApplicationAuthEndpointSupport(AppServerProperties properties,
                                          ApplicationRepository applicationRepository,
                                          KinoticJwtIssuer jwtIssuer,
                                          OrgSignupOidcConfigurationService orgSignupOidcConfigurationService,
                                          OidcFlowOrchestrator oidcFlowOrchestrator,
                                          ParticipantIdentityService identityService,
                                          RefreshTokenService refreshTokenService) {
        super(jwtIssuer, orgSignupOidcConfigurationService, oidcFlowOrchestrator, identityService, refreshTokenService);
        this.properties = properties;
        this.applicationRepository = applicationRepository;
    }

    /**
     * The application whose API host the request was addressed to.
     *
     * @param ctx the request
     * @return the application
     * @throws HttpException with {@code 404} when the request names no application's API host
     */
    public ApplicationKey applicationKey(RoutingContext ctx) {
        ApplicationKey ret = findApplicationKey(ctx);
        if (ret == null) {
            throw new HttpException(404);
        }
        return ret;
    }

    /**
     * {@inheritDoc} The UI is the primary UI of the application the request is addressed to.
     *
     * @throws HttpException with {@code 404} when the request names no application's API host, or names an
     *                       application that does not exist
     * @throws IllegalArgumentException while the application has no primary UI designated, which the
     *                                  gateway answers with {@code 400}
     */
    @Override
    public Future<String> uiUrl(RoutingContext ctx, String path) {
        ApplicationKey applicationKey = findApplicationKey(ctx);
        Future<Application> application = applicationKey != null
                ? applicationRepository.findById(applicationKey.applicationId(), applicationKey.organizationId())
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
    private ApplicationKey findApplicationKey(RoutingContext ctx) {
        // authority() is the request's Host header, parsed; null on an HTTP/1.0 request that sent none
        HostAndPort authority = ctx.request().authority();
        return authority != null ? AppHostUtil.fromHost(authority.host(), properties.getApiBaseUrl()) : null;
    }
}
