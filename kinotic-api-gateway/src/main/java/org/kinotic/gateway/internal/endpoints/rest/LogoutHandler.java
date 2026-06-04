package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import org.springframework.stereotype.Component;

/**
 * The logout route. Globally applicable: a browser has one session cookie regardless of the
 * scope it logged in under (org, application, or system), so destroying that session ends the
 * login for every scope.
 */
@Component
public class LogoutHandler {

    public void mountRoutes(Router router) {
        router.post("/api/logout").handler(this::handleLogout);
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
