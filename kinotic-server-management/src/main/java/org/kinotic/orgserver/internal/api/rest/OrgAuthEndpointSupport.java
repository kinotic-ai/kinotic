package org.kinotic.orgserver.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.services.security.KinoticJwtIssuer;
import org.kinotic.domain.api.services.security.OrgSignupOidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.RefreshTokenService;
import org.kinotic.orgserver.api.config.OrgServerProperties;
import org.springframework.stereotype.Component;

/** The org server's {@link AuthEndpointSupport}: its browser flows send the user to the portal. */
@Component
public class OrgAuthEndpointSupport extends AuthEndpointSupport {

    private final OrgServerProperties properties;

    public OrgAuthEndpointSupport(OrgServerProperties properties,
                                  KinoticJwtIssuer jwtIssuer,
                                  OrgSignupOidcConfigurationService orgSignupOidcConfigurationService,
                                  OidcFlowOrchestrator oidcFlowOrchestrator,
                                  ParticipantIdentityService identityService,
                                  RefreshTokenService refreshTokenService) {
        super(jwtIssuer, orgSignupOidcConfigurationService, oidcFlowOrchestrator, identityService, refreshTokenService);
        this.properties = properties;
    }

    @Override
    public Future<String> uiUrl(RoutingContext ctx, String path) {
        return Future.succeededFuture(properties.getPortalBaseUrl() + path);
    }
}
