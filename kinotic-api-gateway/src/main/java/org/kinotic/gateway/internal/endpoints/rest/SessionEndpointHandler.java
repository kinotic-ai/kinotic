package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.SessionHandler;
import org.kinotic.core.api.security.ConnectedInfo;
import org.springframework.stereotype.Component;

/**
 * Browser session-lifecycle routes (named to avoid clashing with Vert.x's own {@code SessionHandler}):
 * <ul>
 *   <li>{@code GET /api/me} — reports whether the session cookie still authenticates the caller.
 *       A cookie-auth client (the browser) uses this to decide whether to open the realtime
 *       WebSocket: a rejected WS upgrade is opaque to browsers, whereas this HTTP status is
 *       readable, so the SPA can fail fast instead of dialing an unauthenticated socket.</li>
 *   <li>{@code POST /api/logout} — destroys the browser session, ending the login for every scope
 *       (one cookie regardless of org/app/system).</li>
 * </ul>
 */
@Component
public class SessionEndpointHandler {

    public void mountRoutes(Router router) {
        router.get("/api/me").handler(this::handleMe);
        router.post("/api/logout").handler(this::handleLogout);
    }

    /**
     * {@code 204} when the session cookie authenticates the caller, {@code 401} otherwise.
     */
    private void handleMe(RoutingContext ctx) {
        boolean authenticated = false;
        // Reading the session via ctx.session() flips the routing context's "accessed" flag, which
        // makes the SessionHandler persist that session — a store write plus a Set-Cookie — even with
        // lazy sessions enabled. So only touch it when the caller presents the session cookie (the
        // SessionHandler's default name, which is what it's configured with); an unauthenticated probe
        // carrying none would otherwise mint and store an empty session on every refresh.
        if (ctx.request().getCookie(SessionHandler.DEFAULT_SESSION_COOKIE_NAME) != null) {
            Session session = ctx.session();
            authenticated = session.get(ConnectedInfo.SESSION_KEY) instanceof ConnectedInfo connectedInfo
                    && connectedInfo.getParticipant() != null;
            if (!authenticated) {
                // Cookie present but no valid login behind it (stale, or freshly created because the
                // prior session was gone): drop it so nothing empty is persisted and the cookie clears.
                session.destroy();
            }
        }
        ctx.response().setStatusCode(authenticated ? 204 : 401).end();
    }

    /** Destroys the browser session. */
    private void handleLogout(RoutingContext ctx) {
        Session session = ctx.session();
        if (session != null) {
            session.destroy();
        }
        ctx.response().setStatusCode(204).end();
    }
}
