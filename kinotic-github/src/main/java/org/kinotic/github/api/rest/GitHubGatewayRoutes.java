package org.kinotic.github.api.rest;

import io.vertx.ext.web.Router;
import lombok.RequiredArgsConstructor;
import org.kinotic.github.internal.api.rest.GitHubInstallCallbackHandler;
import org.kinotic.github.internal.api.rest.GitHubWebhookHandler;
import org.springframework.stereotype.Component;

/**
 * Wires the two GitHub-driven REST routes onto the gateway router. Everything
 * user-initiated goes through Kinotic RPC over STOMP; only GitHub's own callbacks
 * land here as REST.
 * <ul>
 *   <li>{@code GET /api/github/install/callback} — GitHub redirects the browser
 *       here after the install. State is single-use and looked up via the
 *       cluster-wide install-state store, so no session cookie is needed.</li>
 *   <li>{@code POST /api/github/webhook} — GitHub posts every webhook here.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class GitHubGatewayRoutes {

    private final GitHubInstallCallbackHandler callbackHandler;
    private final GitHubWebhookHandler webhookHandler;

    public void mountRoutes(Router router) {
        callbackHandler.mountRoute(router);
        webhookHandler.mountRoute(router);
    }
}
