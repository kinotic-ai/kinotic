package org.kinotic.systemserver.internal.api.rest;

import io.vertx.ext.web.RoutingContext;
import org.kinotic.domain.api.rest.OAuthExtensionGrant;
import org.kinotic.domain.api.rest.OAuthServerHandler;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.services.security.OAuthAuthorizationService;
import org.kinotic.domain.api.services.security.RefreshTokenService;
import org.kinotic.systemserver.api.config.SystemServerProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/** The system server's authorization server, issued under its API base URL. */
@Component
public class SystemOAuthServerHandler extends OAuthServerHandler {

    private final SystemServerProperties properties;

    public SystemOAuthServerHandler(SystemServerProperties properties,
                                    AuthEndpointSupport authEndpointSupport,
                                    OAuthAuthorizationService oauthAuthorizationService,
                                    RefreshTokenService refreshTokenService,
                                    List<OAuthExtensionGrant> extensionGrants) {
        super(authEndpointSupport, oauthAuthorizationService, refreshTokenService, extensionGrants);
        this.properties = properties;
    }

    @Override
    public String issuer(RoutingContext ctx) {
        return properties.getApiBaseUrl();
    }
}
