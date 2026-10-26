package org.kinotic.systemserver.internal.api.rest;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import io.vertx.core.Future;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.core.api.service.SuppliesGatewayRoutes;
import org.springframework.stereotype.Component;

/**
 * Login route for system users ({@code SystemParticipantIdentity}). Password-only:
 * system users are provisioned by Kinotic, so there is no signup, SSO, or social path. On success the browser
 * session is established; the STOMP WebSocket handshake then authenticates from that session cookie.
 */
@Component
@RequiredArgsConstructor
public class SystemLoginHandler implements SuppliesGatewayRoutes {

    private final AuthEndpointSupport authEndpointSupport;
    private final LocalAuthenticationService localAuthenticationService;
    private final ParticipantIdentityService identityService;

    @Override
    public void mountRoutes(Router router) {
        router.post("/api/auth/system/login").handler(this::handleLogin);
    }

    /**
     * {@code POST /api/auth/system/login {email, password}} — the system user's password, so an organization's or an
     * application's user with the same email cannot authenticate here. Generic {@code 401} on any failure.
     */
    private void handleLogin(RoutingContext ctx) {
        authEndpointSupport.handlePasswordLogin(ctx,
                (email, password) -> localAuthenticationService.authenticateSystemUser(email, password)
                                                               .compose(user -> user == null
                                                                       ? Future.succeededFuture()
                                                                       : identityService.resolveParticipant(user, null, null)));
    }
}
