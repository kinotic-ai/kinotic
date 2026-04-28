package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.ext.auth.User;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.internal.security.KinoticJwtIssuer;
import org.kinotic.gateway.internal.endpoints.rest.support.RedirectFlowSessionSupport;
import org.kinotic.os.github.api.config.KinoticGithubProperties;
import org.springframework.stereotype.Component;

/**
 * {@code POST /api/github/install/start}: authenticates the caller via Kinotic JWT
 * (either {@code Authorization: Bearer ...} or {@code ?token=...}), pulls their
 * organizationId from the JWT, generates a CSRF state, stores
 * {@code github.install.state} and {@code github.install.orgId} on the session, and
 * returns the GitHub install URL as JSON. The SPA does {@code window.location = url}.
 * <p>
 * No business logic — verification, persistence, and event publishing all live in the
 * GitHub module's services. Per the gateway's hard rules, handlers stay thin.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubInstallStartHandler {

    static final String S_STATE = "github.install.state";
    static final String S_ORG_ID = "github.install.orgId";

    private final KinoticGithubProperties githubProperties;
    private final KinoticJwtIssuer jwtIssuer;

    public void mountRoute(Router router) {
        router.post(githubProperties.getGithub().getInstallStartPath()).handler(this::handleStart);
    }

    private void handleStart(RoutingContext ctx) {
        String token = extractToken(ctx);
        if (token == null) {
            respondError(ctx, 401, "missing_token");
            return;
        }
        jwtIssuer.authenticate(token)
                .onSuccess(user -> startInstall(ctx, user))
                .onFailure(err -> {
                    log.warn("GitHub install start: invalid token: {}", err.getMessage());
                    respondError(ctx, 401, "invalid_token");
                });
    }

    private void startInstall(RoutingContext ctx, User user) {
        String scopeType = user.principal().getString("scopeType");
        String orgId = user.principal().getString("scopeId");
        if (!"ORGANIZATION".equals(scopeType) || orgId == null || orgId.isBlank()) {
            respondError(ctx, 403, "organization_scope_required");
            return;
        }
        String slug = githubProperties.getGithub().getAppSlug();
        if (slug == null || slug.isBlank()) {
            log.error("kinotic.github.appSlug not configured");
            respondError(ctx, 500, "app_not_configured");
            return;
        }

        Session session = ctx.session();
        session.regenerateId();
        String state = RedirectFlowSessionSupport.randomUrlSafe(32);
        session.put(S_STATE, state);
        session.put(S_ORG_ID, orgId);

        String url = "https://github.com/apps/" + slug + "/installations/new?state=" + state;
        ctx.response().putHeader("Content-Type", "application/json")
           .end("{\"url\":\"" + url + "\"}");
    }

    private static String extractToken(RoutingContext ctx) {
        String header = ctx.request().getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring("Bearer ".length()).trim();
        }
        return ctx.request().getParam("token");
    }

    private static void respondError(RoutingContext ctx, int status, String errorCode) {
        ctx.response().setStatusCode(status).putHeader("Content-Type", "application/json")
           .end("{\"error\":\"" + errorCode + "\"}");
    }
}
