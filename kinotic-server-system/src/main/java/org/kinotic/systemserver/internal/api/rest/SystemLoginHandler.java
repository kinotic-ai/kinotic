package org.kinotic.systemserver.internal.api.rest;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import io.vertx.core.Future;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import org.springframework.stereotype.Component;

/**
 * Login route for platform operators: an account holding a membership of the platform. Password-only:
 * operators are provisioned by Kinotic, so there is no signup, SSO, or social path. On success the browser
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
     * {@code POST /api/auth/system/login {email, password}} — the account's password, then its membership of
     * the platform, so an account with no such membership cannot authenticate here. Generic {@code 401} on
     * any failure.
     */
    private void handleLogin(RoutingContext ctx) {
        authEndpointSupport.handlePasswordLogin(ctx,
                (email, password) -> localAuthenticationService.authenticateLocal(email, password, null, null)
                                                               .compose(account -> account == null
                                                                       ? Future.succeededFuture()
                                                                       : identityService.resolveParticipant(account.getId(), null, null)));
    }
}
