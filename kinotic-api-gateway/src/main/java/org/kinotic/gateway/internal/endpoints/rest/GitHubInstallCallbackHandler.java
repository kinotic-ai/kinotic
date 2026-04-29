package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.gateway.internal.endpoints.rest.support.RedirectFlowSessionSupport;
import org.kinotic.os.github.api.config.KinoticGithubProperties;
import org.kinotic.os.github.api.model.GitHubAppInstallation;
import org.kinotic.os.github.internal.api.services.GitHubAppInstallationStore;
import org.kinotic.os.github.internal.client.GitHubApiClient;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * {@code GET /api/github/install/callback}: validates the CSRF state cookied during
 * {@link GitHubInstallStartHandler}, calls GitHub for installation metadata, persists
 * a {@link GitHubAppInstallation} bound to the session's {@code orgId}, and redirects
 * the browser to the SPA's success path. Any error redirects the browser to the
 * SPA's success path with {@code ?error=<code>} so the SPA can show an inline message.
 * <p>
 * Persistence is delegated to {@link GitHubAppInstallationStore}: at this point in the
 * flow there is no Kinotic Participant on the request, so the org-scoped CRUD service
 * can't be used — the orgId from the session (validated against the CSRF state) is
 * the security boundary.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubInstallCallbackHandler {

    private final KinoticGithubProperties githubProperties;
    private final GitHubApiClient apiClient;
    private final GitHubAppInstallationStore installationStore;

    public void mountRoute(Router router) {
        router.get(githubProperties.getGithub().getInstallCallbackPath()).handler(this::handleCallback);
    }

    private void handleCallback(RoutingContext ctx) {
        String installationId = ctx.request().getParam("installation_id");
        String setupAction = ctx.request().getParam("setup_action");
        String state = ctx.request().getParam("state");

        Session session = ctx.session();
        String orgId = session != null ? session.<String>get(GitHubInstallStartHandler.S_ORG_ID) : null;
        String validatedState = RedirectFlowSessionSupport.validateAndConsumeState(
                session, GitHubInstallStartHandler.S_STATE, state);
        if (session != null) {
            session.remove(GitHubInstallStartHandler.S_ORG_ID);
        }

        if (validatedState == null || orgId == null) {
            log.warn("GitHub install callback state mismatch (orgId={}, state present={})",
                     orgId, state != null);
            redirect(ctx, "state_mismatch");
            return;
        }
        if (installationId == null || installationId.isBlank()) {
            log.warn("GitHub install callback missing installation_id (setup={})", setupAction);
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
