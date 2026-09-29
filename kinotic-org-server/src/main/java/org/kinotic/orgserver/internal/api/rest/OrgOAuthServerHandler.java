package org.kinotic.orgserver.internal.api.rest;

import io.vertx.ext.web.RoutingContext;
import org.kinotic.domain.api.rest.OAuthExtensionGrant;
import org.kinotic.domain.api.rest.OAuthServerHandler;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.services.security.OAuthAuthorizationService;
import org.kinotic.domain.api.services.security.RefreshTokenService;
import org.kinotic.orgserver.api.config.OrgServerProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/** The org server's authorization server, issued under {@code kinotic.orgServer.issuerBaseUrl} or its API base URL. */
@Component
public class OrgOAuthServerHandler extends OAuthServerHandler {

    private final OrgServerProperties properties;

    public OrgOAuthServerHandler(OrgServerProperties properties,
                                 AuthEndpointSupport authEndpointSupport,
                                 OAuthAuthorizationService oauthAuthorizationService,
                                 RefreshTokenService refreshTokenService,
                                 List<OAuthExtensionGrant> extensionGrants) {
        super(authEndpointSupport, oauthAuthorizationService, refreshTokenService, extensionGrants);
        this.properties = properties;
    }

    @Override
    public String issuer(RoutingContext ctx) {
        return properties.resolveIssuerBaseUrl();
    }
}
