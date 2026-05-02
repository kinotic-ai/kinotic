package org.kinotic.github.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.github.api.model.GitHubAppInstallation;
import org.kinotic.github.api.rest.GithubConstants;
import org.kinotic.github.api.services.GitHubAppInstallationService;
import org.kinotic.github.internal.api.services.GitHubInstallStateService;
import org.kinotic.github.internal.api.services.client.GitHubApiClient;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * {@code GET /api/github/install/callback}: GitHub redirects the browser here after
 * the user finishes the install. The {@code state} query parameter was minted by the
 * RPC {@code GitHubAppInstallationService.startInstall()} call earlier in the same
 * STOMP session — atomically consuming it from {@link GitHubInstallStateService}
 * yields the orgId the install was started for. There is no session cookie or JWT
 * involved on this REST roundtrip.
 * <p>
 * Persistence goes through {@link GitHubAppInstallationService} under
 * {@link SecurityContext#withElevatedAccess(java.util.function.Supplier) elevated
 * access} — the org-scoped CRUD enforcement can't apply because there's no Kinotic
 * Participant on this REST roundtrip; the state-staged orgId is the security boundary.
 * <p>
 * Errors redirect the browser to the SPA's success path with {@code ?error=<code>}
 * so the SPA can render an inline message.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubInstallCallbackHandler {

    private final GitHubApiClient apiClient;
    private final GitHubAppInstallationService installationService;
    private final GitHubInstallStateService stateService;
    private final SecurityContext securityContext;

    public void mountRoute(Router router) {
        router.get(GithubConstants.INSTALL_CALLBACK_PATH).handler(this::handleCallback);
    }

    private void handleCallback(RoutingContext ctx) {
        String installationIdParam = ctx.request().getParam("installation_id");
        String state = ctx.request().getParam("state");

        String orgId = stateService.consume(state);
        if (orgId == null) {
            log.warn("GitHub install callback rejected — unknown or expired state");
            redirect(ctx, "state_mismatch");
            return;
        }
        long installationId;
        try {
            installationId = Long.parseLong(installationIdParam);
        } catch (NumberFormatException e) {
            log.warn("GitHub install callback rejected — invalid installation_id={} (org {})",
                     installationIdParam, orgId);
            redirect(ctx, "missing_installation_id");
            return;
        }

        apiClient.getInstallation(installationId)
                .compose(json -> persist(orgId, installationId, json))
                .onSuccess(install -> redirectSuccess(ctx, install))
                .onFailure(err -> {
                    log.warn("GitHub install callback failed: {}", err.getMessage());
                    redirect(ctx, "callback_failed");
                });
    }

    private Future<GitHubAppInstallation> persist(String orgId, long installationId, JsonObject githubJson) {
        JsonObject account = githubJson.getJsonObject("account");
        Date now = new Date();
        GitHubAppInstallation install = new GitHubAppInstallation()
                .setId(Long.toString(installationId))
                .setOrganizationId(orgId)
                .setGithubInstallationId(installationId)
                .setAccountLogin(account != null ? account.getString("login") : null)
                .setAccountType(account != null ? account.getString("type") : null)
                .setCreated(now)
                .setUpdated(now);
        return Future.fromCompletionStage(
                securityContext.withElevatedAccess(() -> installationService.save(install)));
    }

    private void redirectSuccess(RoutingContext ctx, GitHubAppInstallation install) {
        String location = GithubConstants.INSTALL_SUCCESS_PATH
                + "?installationId=" + URLEncoder.encode(install.getId(), StandardCharsets.UTF_8);
        ctx.response().setStatusCode(302).putHeader("Location", location).end();
    }

    private void redirect(RoutingContext ctx, String errorCode) {
        String location = GithubConstants.INSTALL_SUCCESS_PATH
                + "?error=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8);
        ctx.response().setStatusCode(302).putHeader("Location", location).end();
    }
}
