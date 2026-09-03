package org.kinotic.domain.internal.api.rest;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.internal.api.rest.support.AuthEndpointSupport;
import org.springframework.stereotype.Component;

/**
 * Login route for platform operators (a SYSTEM-scope {@link UserParticipantIdentity} —
 * {@code organizationId} and {@code applicationId} both null). Password-only: system users are
 * provisioned by Kinotic, so there is no signup, SSO, or social path. On success the browser
 * session is established; the STOMP WebSocket handshake then authenticates from that session cookie.
 */
@Component
@RequiredArgsConstructor
public class SystemLoginHandler implements SuppliesGatewayRoutes {

    private final AuthEndpointSupport authEndpointSupport;
    private final LocalAuthenticationService localAuthenticationService;

    @Override
    public void mountRoutes(Router router) {
        router.post("/api/auth/system/login").handler(this::handleLogin);
    }

    /**
     * {@code POST /api/auth/system/login {email, password}} — local password auth, scoped to
     * SYSTEM so a stray cross-scope match can't authenticate here. Generic {@code 401} on any
     * failure.
     */
    private void handleLogin(RoutingContext ctx) {
        authEndpointSupport.handlePasswordLogin(ctx,
                (email, password) -> localAuthenticationService.authenticateLocal(
                        email, password, null, null));
    }
}
