package org.kinotic.test;

import io.vertx.ext.web.RoutingContext;
import org.kinotic.domain.api.rest.OAuthExtensionGrant;
import org.kinotic.domain.api.rest.OAuthServerHandler;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.services.security.OAuthAuthorizationService;
import org.kinotic.domain.api.services.security.RefreshTokenService;
import org.springframework.stereotype.Component;

import java.util.List;

/** The test node's authorization server, issued under the URL its UI is served at. */
@Component
public class TestOAuthServerHandler extends OAuthServerHandler {

    public TestOAuthServerHandler(AuthEndpointSupport authEndpointSupport,
                                  OAuthAuthorizationService oauthAuthorizationService,
                                  RefreshTokenService refreshTokenService,
                                  List<OAuthExtensionGrant> extensionGrants) {
        super(authEndpointSupport, oauthAuthorizationService, refreshTokenService, extensionGrants);
    }

    @Override
    public String issuer(RoutingContext ctx) {
        return TestAuthEndpointSupport.UI_BASE_URL;
    }
}
