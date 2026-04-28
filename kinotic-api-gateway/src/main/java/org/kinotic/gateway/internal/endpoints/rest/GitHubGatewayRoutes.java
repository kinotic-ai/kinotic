package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import lombok.RequiredArgsConstructor;
import org.kinotic.gateway.internal.endpoints.rest.support.RedirectFlowSessionSupport;
import org.springframework.stereotype.Component;

/**
 * Wires the three GitHub-related routes onto the gateway router. The install routes
 * share a {@code /api/github/install/*} session handler (state cookie); the webhook
 * route deliberately does <em>not</em> use the session since GitHub posts to it from
 * outside the SPA.
 */
@Component
@RequiredArgsConstructor
public class GitHubGatewayRoutes {

    private final Vertx vertx;
    private final GitHubInstallStartHandler startHandler;
    private final GitHubInstallCallbackHandler callbackHandler;
    private final GitHubWebhookHandler webhookHandler;

    public void mountRoutes(Router router) {
        router.route("/api/github/install/*").handler(RedirectFlowSessionSupport.newSessionHandler(vertx));
        startHandler.mountRoute(router);
        callbackHandler.mountRoute(router);
        webhookHandler.mountRoute(router);
    }
}
