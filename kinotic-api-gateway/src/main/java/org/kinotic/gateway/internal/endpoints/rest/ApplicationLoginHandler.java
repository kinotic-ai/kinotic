package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.gateway.internal.endpoints.rest.support.AuthEndpointSupport;
import org.kinotic.gateway.internal.endpoints.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.model.iam.AuthType;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.OidcConfiguration;
import org.kinotic.os.api.services.ApplicationService;
import org.kinotic.domain.api.services.iam.IamUserService;
import org.kinotic.domain.api.services.iam.LocalAuthenticationService;
import org.kinotic.os.api.services.iam.OidcConfigurationService;
import org.springframework.stereotype.Component;

/**
 * Login routes for end-users of an application built on Kinotic. Distinct from the
 * org-login handler: the user is logging into an application's own user base (an
 * APP-scope {@link IamUser} — {@code organizationId} + {@code applicationId} both set),
 * not into the platform-managed org admin surface.
 *
 * <ul>
 *   <li>{@code GET /api/app/:orgId/:appId/login/providers} — lists the enabled
 *       {@link OidcConfiguration} rows the app references via
 *       {@code Application.oidcConfigurationIds}.</li>
 *   <li>{@code POST /api/app/:orgId/:appId/login/lookup {email}} — email-first SSO/password
 *       decision. If the IamUser is OIDC and their config is live, returns
 *       {@code {type: "sso", redirect: "..."}}. Otherwise {@code {type: "password"}}.</li>
 *   <li>{@code POST /api/app/:orgId/:appId/login {email, password}} — local password
 *       auth, scoped to the application so a stray cross-scope match (dev SYSTEM admin,
 *       etc.) can't authenticate against an app endpoint.</li>
 *   <li>{@code GET /api/app/:orgId/:appId/login/callback/:configId} — IdP returns here.</li>
 * </ul>
 *
 * <p>The {@code :orgId} path param disambiguates between orgs that happen to share an
 * appId — every IamUser/OidcConfiguration lookup carries both ids so the auth path can
 * never collide cross-org.
 *
 * <p>OIDC config lookups run inside {@link SecurityContext#withElevatedAccess} — the
 * pre-auth login flow has no participant bound to filter against. Phase 5a replaces those
 * wrappers with direct repository calls.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationLoginHandler {

    private final IamUserService iamUserService;
    private final ApplicationService applicationService;
    private final OidcConfigurationService oidcConfigurationService;
    private final LocalAuthenticationService localAuthenticationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final SecurityContext securityContext;
    private final AuthEndpointSupport authEndpointSupport;

    public void mountRoutes(Router router) {
        router.get(OidcConstants.APP_LOGIN_BASE + "/providers").handler(this::handleProviders);
        router.post(OidcConstants.APP_LOGIN_BASE + "/lookup").handler(this::handleLookup);
        router.post(OidcConstants.APP_LOGIN_BASE).handler(this::handleLogin);
        router.get(OidcConstants.APP_LOGIN_BASE + "/callback/:configId").handler(this::handleCallback);
    }

    private void handleProviders(RoutingContext ctx) {
        String orgId = ctx.pathParam("orgId");
        String appId = ctx.pathParam("appId");
        securityContext.withElevatedAccess(() -> applicationService.getOidcConfigurations(appId))
              .whenComplete((configs, err) -> {
                  if (err != null) {
                      log.warn("Failed to list app providers for {}/{}: {}", orgId, appId, err.getMessage());
                      authEndpointSupport.respondError(ctx, 500, "Failed to list providers");
                      return;
                  }
                  authEndpointSupport.respondProvidersList(ctx, configs);
              });
    }

    private void handleLookup(RoutingContext ctx) {
        String orgId = ctx.pathParam("orgId");
        String appId = ctx.pathParam("appId");
        JsonObject body = ctx.body().asJsonObject();
        String email = body == null ? null : body.getString("email");
        if (email == null || email.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "email is required");
            return;
        }

        Future.fromCompletionStage(iamUserService.findByEmail(email, orgId, appId))
              .compose(user -> resolveSsoOrPassword(ctx, orgId, appId, user))
              .onFailure(err -> {
                  log.warn("App login lookup failed for {}/{}/{}: {}", orgId, appId, email, err.getMessage());
                  authEndpointSupport.respondError(ctx, 500, "Lookup failed");
              });
    }

    private Future<Void> resolveSsoOrPassword(RoutingContext ctx, String orgId, String appId, IamUser user) {
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
                         return oidcFlowOrchestrator.startFlow(ctx, match, callbackUrl(orgId, appId, match.getId()), null)
                                 .compose(url -> authEndpointSupport.respondSsoRedirect(ctx, url));
                     });
    }

    private void handleLogin(RoutingContext ctx) {
        String orgId = ctx.pathParam("orgId");
        String appId = ctx.pathParam("appId");
        authEndpointSupport.handlePasswordLogin(ctx,
                (email, password) -> localAuthenticationService.authenticateLocal(
                        email, password, orgId, appId));
    }

    private void handleCallback(RoutingContext ctx) {
        String orgId = ctx.pathParam("orgId");
        String appId = ctx.pathParam("appId");
        String pathConfigId = ctx.pathParam("configId");

        oidcFlowOrchestrator.<OidcConfiguration>handleCallback(
                ctx, pathConfigId, callbackUrl(orgId, appId, pathConfigId),
                id -> securityContext.withElevatedAccess(() -> oidcConfigurationService.findById(id)))
                .onSuccess(result -> authEndpointSupport.completeOidcLogin(ctx, result.config(), result.claims(),
                        sub -> iamUserService.findByOidcIdentity(
                                sub, result.config().getId(), orgId, appId)))
                .onFailure(ex -> authEndpointSupport.redirectCallbackFailure(ctx, ex));
    }

    private String callbackUrl(String orgId, String appId, String configId) {
        return authEndpointSupport.absoluteUrl("/api/app/" + orgId + "/" + appId + "/login/callback/" + configId);
    }
}
