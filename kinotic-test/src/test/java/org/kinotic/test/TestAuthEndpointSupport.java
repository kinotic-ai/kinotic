package org.kinotic.test;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.services.security.KinoticJwtIssuer;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.RefreshTokenService;
import org.springframework.stereotype.Component;

/**
 * The test node's {@link AuthEndpointSupport}: every module runs in this one node, so it presents the UI
 * the management server presents in development.
 */
@Component
public class TestAuthEndpointSupport extends AuthEndpointSupport {

    static final String UI_BASE_URL = "http://localhost:9090";

    public TestAuthEndpointSupport(KinoticJwtIssuer jwtIssuer,
                                   OidcConfigurationService oidcConfigurationService,
                                   OidcFlowOrchestrator oidcFlowOrchestrator,
                                   ParticipantIdentityService identityService,
                                   RefreshTokenService refreshTokenService) {
        super(jwtIssuer, oidcConfigurationService, oidcFlowOrchestrator, identityService, refreshTokenService);
    }

    @Override
    public Future<String> uiUrl(RoutingContext ctx, String path) {
        return Future.succeededFuture(UI_BASE_URL + path);
    }
}
