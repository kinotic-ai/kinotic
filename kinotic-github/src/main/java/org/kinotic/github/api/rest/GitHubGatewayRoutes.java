package org.kinotic.github.api.rest;

import io.vertx.ext.web.Router;
import lombok.RequiredArgsConstructor;
import org.kinotic.github.internal.api.rest.GitHubWebhookHandler;
import org.springframework.stereotype.Component;

/**
 * Wires GitHub-driven REST routes onto the gateway router. The post-install browser
 * redirect is handled by the SPA (which then calls
 * {@code GitHubAppInstallationService.completeInstall} via RPC); only GitHub's
 * webhook delivery lands here.
 * <ul>
 *   <li>{@code POST /api/github/webhook} — GitHub posts every webhook here.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class GitHubGatewayRoutes {

    private final GitHubWebhookHandler webhookHandler;

    public void mountRoutes(Router router) {
        webhookHandler.mountRoute(router);
    }
}
