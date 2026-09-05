package org.kinotic.domain.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.internal.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.internal.api.rest.support.CallbackResult;
import org.kinotic.domain.internal.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.model.security.AuthType;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.internal.api.repositories.OidcConfigurationRepository;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.springframework.stereotype.Component;


/**
 * Login routes for end-users of an application built on Kinotic. Distinct from the
 * org-login handler: the user is logging into an application's own user base (an
 * APP-scope {@link UserParticipantIdentity} — {@code organizationId} + {@code applicationId} both set),
 * not into the platform-managed org admin surface. Every lookup carries both path ids so
 * the auth path can never collide cross-org, and OIDC flows started here return to this
 * handler's own callback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationLoginHandler implements SuppliesGatewayRoutes {

    private final ParticipantIdentityService identityService;
    private final OidcConfigurationService oidcConfigurationService;
    private final OidcConfigurationRepository oidcConfigurationRepository;
    private final LocalAuthenticationService localAuthenticationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final AuthEndpointSupport authEndpointSupport;

    @Override
    public void mountRoutes(Router router) {
        router.get("/api/auth/app/:orgId/:appId/login/providers").handler(this::handleProviders);
        router.post("/api/auth/app/:orgId/:appId/login/lookup").handler(this::handleLookup);
        router.post("/api/auth/app/:orgId/:appId/login").handler(this::handleLogin);
        router.get("/api/auth/app/:orgId/:appId/login/oidc/callback/:configId").handler(this::handleCallback);
    }

    /**
     * {@code GET /api/auth/app/:orgId/:appId/login/providers} — lists the enabled
     * {@link OidcConfiguration} rows the app references via
     * {@code Application.oidcConfigurationIds}.
     */
    private void handleProviders(RoutingContext ctx) {
        String orgId = ctx.pathParam("orgId");
        String appId = ctx.pathParam("appId");
        oidcConfigurationService.findEnabledForScope(orgId, appId)
              .onSuccess(configs -> authEndpointSupport.respondProvidersList(ctx, configs))
              .onFailure(err -> {
                  log.warn("Failed to list app providers for {}/{}: {}", orgId, appId, err.getMessage());
                  authEndpointSupport.respondError(ctx, 500, "Failed to list providers");
              });
    }

    /**
     * {@code POST /api/auth/app/:orgId/:appId/login/lookup {email}} — email-first SSO/password
     * decision: {@code {type:"sso", redirect:"…"}} when the user is OIDC with a live
     * config, otherwise {@code {type:"password"}}.
     */
    private void handleLookup(RoutingContext ctx) {
        String orgId = ctx.pathParam("orgId");
        String appId = ctx.pathParam("appId");
        JsonObject body = authEndpointSupport.readJsonBody(ctx);
        String email = body.getString("email");
        if (email == null || email.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "email is required");
            return;
        }

        identityService.findByEmail(email, orgId, appId)
              .compose(user -> resolveSsoOrPassword(ctx, orgId, appId, user))
              .onFailure(err -> {
                  log.warn("App login lookup failed for {}/{}/{}: {}", orgId, appId, email, err.getMessage());
                  authEndpointSupport.respondError(ctx, 500, "Lookup failed");
              });
    }

    private Future<Void> resolveSsoOrPassword(RoutingContext ctx, String orgId, String appId, UserParticipantIdentity user) {
        if (user == null
                || user.getAuthType() != AuthType.OIDC
                || user.getOidcConfigId() == null) {
            return authEndpointSupport.respondPasswordPath(ctx);
        }

        String configId = user.getOidcConfigId();
        return oidcConfigurationRepository.findById(configId, orgId)
                     .compose(match -> {
                         if (match == null || !match.isEnabled()) {
                             return authEndpointSupport.respondPasswordPath(ctx);
                         }
                         return oidcFlowOrchestrator.startFlow(ctx, match, callbackUrl(orgId, appId, match.getId()), null)
                                 .compose(url -> authEndpointSupport.respondSsoRedirect(ctx, url));
                     });
    }

    /**
     * {@code POST /api/auth/app/:orgId/:appId/login {email, password}} — local password auth,
     * scoped to the application so a stray cross-scope match can't authenticate here.
     */
    private void handleLogin(RoutingContext ctx) {
        String orgId = ctx.pathParam("orgId");
        String appId = ctx.pathParam("appId");
        authEndpointSupport.handlePasswordLogin(ctx,
                (email, password) -> localAuthenticationService.authenticateLocal(
                        email, password, orgId, appId));
    }

    /**
     * {@code GET /api/auth/app/:orgId/:appId/login/oidc/callback/:configId} — the IdP returns here;
     * validates the callback and logs the user into the application. The pre-auth flow has
     * no participant bound, so the config lookup is scoped by the {@code :orgId} path param.
     */
    private void handleCallback(RoutingContext ctx) {
        String orgId = ctx.pathParam("orgId");
        String appId = ctx.pathParam("appId");
        String pathConfigId = ctx.pathParam("configId");

        oidcFlowOrchestrator.<OidcConfiguration>handleCallback(
                ctx, pathConfigId, callbackUrl(orgId, appId, pathConfigId),
                _ -> oidcConfigurationRepository.findById(pathConfigId, orgId))
                .onSuccess(result -> completeAppLogin(ctx, result, orgId, appId))
                .onFailure(ex -> authEndpointSupport.redirectCallbackFailure(ctx, ex));
    }

    private void completeAppLogin(RoutingContext ctx,
                                  CallbackResult<OidcConfiguration> result,
                                  String orgId,
                                  String appId) {
        authEndpointSupport.completeOidcLogin(ctx, result.config(), result.claims(),
                sub -> identityService.findByOidcIdentity(sub, result.config().getId(), orgId, appId));
    }

    private String callbackUrl(String orgId, String appId, String configId) {
        return authEndpointSupport.absoluteUrl("/api/auth/app/" + orgId + "/" + appId + "/login/oidc/callback/" + configId);
    }
}
