package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.gateway.internal.endpoints.JsonSessionCodec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

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
@RequiredArgsConstructor
public class SessionEndpointHandler {

    private final JsonMapper jsonMapper;

    public void mountRoutes(Router router) {
        router.get("/api/me").handler(this::handleMe);
        router.post("/api/logout").handler(this::handleLogout);
    }

    /**
     * {@code 204} when the session cookie authenticates the caller, {@code 401} otherwise. The
     * SessionHandler on {@code /api/*} has already resolved the cookie into the session by the time
     * this runs, so the check is a session read.
     */
    private void handleMe(RoutingContext ctx) {
        ConnectedInfo connectedInfo = JsonSessionCodec.read(ctx.session(), jsonMapper,
                                                            ConnectedInfo.SESSION_KEY, ConnectedInfo.class);
        boolean authenticated = connectedInfo != null && connectedInfo.getParticipant() != null;
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
