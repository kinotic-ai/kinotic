package org.kinotic.domain.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.core.api.security.ConnectionRevocationService;
import org.kinotic.core.api.security.SessionBinding;
import org.kinotic.core.api.service.SuppliesGatewayRoutes;
import org.springframework.stereotype.Component;

/**
 * Browser session-lifecycle routes (named to avoid clashing with Vert.x's own {@code SessionHandler}).
 * Both act on the login of the page that sends the request (see {@link SessionBinding}):
 * <ul>
 *   <li>{@code GET /api/auth/me} — reports whether the session cookie authenticates the calling page.
 *       A cookie-auth client (the browser) uses this to decide whether to open the realtime
 *       WebSocket: a rejected WS upgrade is opaque to browsers, whereas this HTTP status is
 *       readable, so the SPA can fail fast instead of dialing an unauthenticated socket.</li>
 *   <li>{@code POST /api/auth/logout} — ends the calling page's login and the realtime connections it
 *       established. The session, and its cookie, end once no page's login remains.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SessionEndpointHandler implements SuppliesGatewayRoutes {

    private final ConnectionRevocationService connectionRevocationService;

    @Override
    public void mountRoutes(Router router) {
        router.get("/api/auth/me").handler(this::handleMe);
        router.post("/api/auth/logout").handler(this::handleLogout);
    }

    /**
     * {@code 204} when the session cookie holds a login for the calling page, {@code 401} otherwise.
     */
    private void handleMe(RoutingContext ctx) {
        ConnectedInfo connectedInfo = SessionBinding.connectedInfo(ctx);
        boolean authenticated = connectedInfo != null && connectedInfo.getParticipant() != null;
        if (!authenticated) {
            // a cookie with no login behind it (stale, or freshly created because the prior session was
            // gone) clears, so nothing empty is persisted; another page's login or sign-in in progress
            // keeps the session
            SessionBinding.unbind(ctx);
        }
        ctx.response().setStatusCode(authenticated ? 204 : 401).end();
    }

    /** Ends the calling page's login and the connections it established. */
    private void handleLogout(RoutingContext ctx) {
        String loginId = SessionBinding.loginId(ctx);
        SessionBinding.unbind(ctx);
        Future<Void> revoked;
        if (loginId != null) {
            revoked = connectionRevocationService.revokeLogin(loginId);
        } else {
            revoked = Future.succeededFuture();
        }
        revoked.onComplete(ar -> ctx.response().setStatusCode(204).end());
    }
}
