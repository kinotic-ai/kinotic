package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.security.AuthScopeType;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.gateway.internal.auth.AuthEndpointSupport;
import org.kinotic.gateway.internal.auth.OidcFlowOrchestrator;
import org.kinotic.gateway.internal.auth.SessionKeys;
import org.kinotic.os.api.model.iam.AuthType;
import org.kinotic.os.api.model.iam.IamUser;
import org.kinotic.os.api.model.iam.OidcConfiguration;
import org.kinotic.os.api.services.ApplicationService;
import org.kinotic.os.api.services.iam.IamUserService;
import org.kinotic.os.api.services.iam.LocalAuthenticationService;
import org.kinotic.os.api.services.iam.OidcConfigurationService;
import org.springframework.stereotype.Component;

/**
 * Login routes for end-users of an application built on Kinotic. Distinct from the
 * org-login handler: the user is logging into an application's own user base (an
 * {@link IamUser} with {@code authScopeType=APPLICATION}, {@code authScopeId=<appId>}),
 * not into the platform-managed org admin surface.
 *
 * <ul>
 *   <li>{@code GET /api/app/:appId/login/providers} — lists the enabled
 *       {@link OidcConfiguration} rows the app references via
 *       {@code Application.oidcConfigurationIds}.</li>
 *   <li>{@code POST /api/app/:appId/login/lookup {email}} — email-first SSO/password
 *       decision. If the IamUser is OIDC and their config is live, returns
 *       {@code {type: "sso", redirect: "..."}}. Otherwise {@code {type: "password"}}.</li>
 *   <li>{@code POST /api/app/:appId/login/token {email, password}} — local password auth,
 *       scoped to the application so a stray cross-scope match (dev SYSTEM admin, etc.)
 *       can't authenticate against an app endpoint.</li>
 *   <li>{@code GET /api/app/:appId/login/callback/:configId} — IdP returns here.</li>
 * </ul>
 *
 * <p>OIDC config lookups run inside {@link SecurityContext#withElevatedAccess} — the
 * pre-auth login flow has no participant bound to filter against, and the configId is
 * trusted (it came from the IamUser row resolved by email or from the IdP redirect we
 * issued ourselves).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationLoginHandler {

    private static final SessionKeys SESSION_KEYS = SessionKeys.ofPrefix("app-login");

    private final IamUserService iamUserService;
    private final ApplicationService applicationService;
    private final OidcConfigurationService oidcConfigurationService;
    private final LocalAuthenticationService localAuthenticationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final SecurityContext securityContext;
    private final AuthEndpointSupport authEndpointSupport;

    public void mountRoutes(Router router) {
        authEndpointSupport.installSessionHandler(router, OidcConstants.APP_LOGIN_BASE);

        router.get(OidcConstants.APP_LOGIN_BASE + "/providers").handler(this::handleProviders);
        router.post(OidcConstants.APP_LOGIN_BASE + "/lookup").handler(this::handleLookup);
        router.post(OidcConstants.APP_LOGIN_BASE + "/token").handler(this::handleToken);
        router.get(OidcConstants.APP_LOGIN_BASE + "/callback/:configId").handler(this::handleCallback);
    }

    private void handleProviders(RoutingContext ctx) {
        String appId = ctx.pathParam("appId");
        securityContext.withElevatedAccess(() -> applicationService.getOidcConfigurations(appId))
              .whenComplete((configs, err) -> {
                  if (err != null) {
                      log.warn("Failed to list app providers for {}: {}", appId, err.getMessage());
                      authEndpointSupport.respondError(ctx, 500, "Failed to list providers");
                      return;
                  }
                  authEndpointSupport.respondProvidersList(ctx, configs);
              });
    }

    private void handleLookup(RoutingContext ctx) {
        String appId = ctx.pathParam("appId");
        JsonObject body = ctx.body().asJsonObject();
        String email = body == null ? null : body.getString("email");
        if (email == null || email.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "email is required");
            return;
        }

        Future.fromCompletionStage(iamUserService.findByEmailAndScope(email, AuthScopeType.APPLICATION.name(), appId))
              .compose(user -> resolveSsoOrPassword(ctx, appId, user))
              .onFailure(err -> {
                  log.warn("App login lookup failed for {}/{}: {}", appId, email, err.getMessage());
                  authEndpointSupport.respondError(ctx, 500, "Lookup failed");
              });
    }

    private Future<Void> resolveSsoOrPassword(RoutingContext ctx, String appId, IamUser user) {
        if (user == null
                || user.getAuthType() != AuthType.OIDC
                || user.getOidcConfigId() == null) {
            return authEndpointSupport.respondPasswordPath(ctx);
        }

        String configId = user.getOidcConfigId();
        return Future.fromCompletionStage(
                        securityContext.withElevatedAccess(() -> oidcConfigurationService.findById(configId)))
                     .compose(match -> {
                         if (match == null || !match.isEnabled()) {
                             return authEndpointSupport.respondPasswordPath(ctx);
                         }
                         return oidcFlowOrchestrator.startFlow(ctx, match, SESSION_KEYS,
                                                               callbackUrl(appId, match.getId()), null)
                                 .compose(url -> authEndpointSupport.respondSsoRedirect(ctx, url));
                     });
    }

    private void handleToken(RoutingContext ctx) {
        String appId = ctx.pathParam("appId");
        authEndpointSupport.handlePasswordToken(ctx,
                (email, password) -> localAuthenticationService.authenticateLocal(
                        email, password, AuthScopeType.APPLICATION.name(), appId));
    }

    private void handleCallback(RoutingContext ctx) {
        String appId = ctx.pathParam("appId");
        String pathConfigId = ctx.pathParam("configId");

        oidcFlowOrchestrator.<OidcConfiguration>handleCallback(
                ctx, pathConfigId, SESSION_KEYS, callbackUrl(appId, pathConfigId),
                id -> securityContext.withElevatedAccess(() -> oidcConfigurationService.findById(id)))
                .onSuccess(result -> authEndpointSupport.completeOidcLogin(ctx, result.config(), result.claims(),
                        sub -> iamUserService.findByOidcIdentityAndScope(
                                sub, result.config().getId(), AuthScopeType.APPLICATION.name(), appId)))
                .onFailure(ex -> authEndpointSupport.redirectCallbackFailure(ctx, ex));
    }

    private String callbackUrl(String appId, String configId) {
        return authEndpointSupport.absoluteUrl("/api/app/" + appId + "/login/callback/" + configId);
    }
}
