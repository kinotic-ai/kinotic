package org.kinotic.appserver.internal.api.rest;

import io.vertx.ext.web.RoutingContext;
import org.kinotic.appserver.api.config.AppServerProperties;
import org.kinotic.domain.api.rest.OAuthExtensionGrant;
import org.kinotic.domain.api.rest.OAuthServerHandler;
import org.kinotic.domain.api.services.security.OAuthAuthorizationService;
import org.kinotic.domain.api.services.security.RefreshTokenService;
import org.springframework.stereotype.Component;

import java.util.List;

/** The app server's authorization server: each application is its own issuer, at its own API host. */
@Component
public class ApplicationOAuthServerHandler extends OAuthServerHandler {

    private final ApplicationAuthEndpointSupport authEndpointSupport;
    private final AppServerProperties properties;

    public ApplicationOAuthServerHandler(ApplicationAuthEndpointSupport authEndpointSupport,
                                         AppServerProperties properties,
                                         OAuthAuthorizationService oauthAuthorizationService,
                                         RefreshTokenService refreshTokenService,
                                         List<OAuthExtensionGrant> extensionGrants) {
        super(authEndpointSupport, oauthAuthorizationService, refreshTokenService, extensionGrants);
        // the base holds it as AuthEndpointSupport; this server's issuer needs the application it names
        this.authEndpointSupport = authEndpointSupport;
        this.properties = properties;
    }

    @Override
    public String issuer(RoutingContext ctx) {
        // an application's API host is reached the same way by a browser and by an MCP host's backend
        return authEndpointSupport.appHost(ctx).apiUrl(properties.getApiBaseUrl());
    }
}
