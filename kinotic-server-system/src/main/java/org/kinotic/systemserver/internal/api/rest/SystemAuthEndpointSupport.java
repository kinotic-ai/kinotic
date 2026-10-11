package org.kinotic.systemserver.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.services.security.KinoticJwtIssuer;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.RefreshTokenService;
import org.kinotic.systemserver.api.config.SystemServerProperties;
import org.springframework.stereotype.Component;

/** The system server's {@link AuthEndpointSupport}: its browser flows send the user to the system console. */
@Component
public class SystemAuthEndpointSupport extends AuthEndpointSupport {

    private final SystemServerProperties properties;

    public SystemAuthEndpointSupport(SystemServerProperties properties,
                                     KinoticJwtIssuer jwtIssuer,
                                     OidcConfigurationService oidcConfigurationService,
                                     OidcFlowOrchestrator oidcFlowOrchestrator,
                                     ParticipantIdentityService identityService,
                                     RefreshTokenService refreshTokenService) {
        super(jwtIssuer, oidcConfigurationService, oidcFlowOrchestrator, identityService, refreshTokenService);
        this.properties = properties;
    }

    @Override
    public Future<String> uiUrl(RoutingContext ctx, String path) {
        return Future.succeededFuture(properties.getConsoleBaseUrl() + path);
    }
}
