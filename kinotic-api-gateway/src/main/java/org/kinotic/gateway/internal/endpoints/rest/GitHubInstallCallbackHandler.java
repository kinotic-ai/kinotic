package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.github.api.config.KinoticGithubProperties;
import org.kinotic.os.github.api.model.GitHubAppInstallation;
import org.kinotic.os.github.internal.api.services.GitHubAppInstallationStore;
import org.kinotic.os.github.internal.api.services.GitHubInstallStateStore;
import org.kinotic.os.github.internal.client.GitHubApiClient;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * {@code GET /api/github/install/callback}: GitHub redirects the browser here after
 * the user finishes the install. The {@code state} query parameter was minted by the
 * RPC {@code GitHubAppInstallationService.startInstall()} call earlier in the same
 * STOMP session — atomically consuming it from {@link GitHubInstallStateStore}
 * yields the orgId the install was started for. There is no session cookie or JWT
 * involved on this REST roundtrip.
 * <p>
 * Persistence is delegated to {@link GitHubAppInstallationStore}: at this point in
 * the flow there is no Kinotic Participant on the request, so the org-scoped CRUD
 * service can't be used — the state-staged orgId is the security boundary.
 * <p>
 * Errors redirect the browser to the SPA's success path with {@code ?error=<code>}
 * so the SPA can render an inline message.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubInstallCallbackHandler {

    private final KinoticGithubProperties githubProperties;
    private final GitHubApiClient apiClient;
    private final GitHubAppInstallationStore installationStore;
    private final GitHubInstallStateStore stateStore;

    public void mountRoute(Router router) {
        router.get(githubProperties.getGithub().getInstallCallbackPath()).handler(this::handleCallback);
    }

    private void handleCallback(RoutingContext ctx) {
        String installationId = ctx.request().getParam("installation_id");
        String state = ctx.request().getParam("state");

        String orgId = stateStore.consume(state);
        if (orgId == null) {
            log.warn("GitHub install callback rejected — unknown or expired state");
            redirect(ctx, "state_mismatch");
            return;
        }
        if (installationId == null || installationId.isBlank()) {
            log.warn("GitHub install callback missing installation_id (org {})", orgId);
            redirect(ctx, "missing_installation_id");
            return;
        }

        apiClient.getInstallation(installationId)
                .compose(json -> Future.fromCompletionStage(
                        installationStore.upsertFromGithub(orgId, installationId, json)))
                .onSuccess(install -> redirectSuccess(ctx, install))
                .onFailure(err -> {
                    log.warn("GitHub install callback failed: {}", err.getMessage());
                    redirect(ctx, "callback_failed");
                });
    }

    private void redirectSuccess(RoutingContext ctx, GitHubAppInstallation install) {
        String location = githubProperties.getGithub().getInstallSuccessPath()
                + "?installationId=" + URLEncoder.encode(install.getId(), StandardCharsets.UTF_8);
        ctx.response().setStatusCode(302).putHeader("Location", location).end();
    }

    private void redirect(RoutingContext ctx, String errorCode) {
        String location = githubProperties.getGithub().getInstallSuccessPath()
                + "?error=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8);
        ctx.response().setStatusCode(302).putHeader("Location", location).end();
    }
}
