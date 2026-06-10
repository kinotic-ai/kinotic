package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.iam.OidcConfiguration;
import org.kinotic.domain.api.model.iam.OrgSignupOidcConfiguration;
import org.kinotic.domain.api.services.iam.IamUserService;
import org.kinotic.domain.api.services.iam.OrgSignupOidcConfigurationService;
import org.kinotic.domain.internal.api.repositories.OidcConfigurationRepository;
import org.kinotic.gateway.internal.endpoints.rest.support.AuthEndpointSupport;
import org.kinotic.gateway.internal.endpoints.rest.support.CallbackResult;
import org.kinotic.gateway.internal.endpoints.rest.support.InviteAcceptSupport;
import org.kinotic.gateway.internal.endpoints.rest.support.OidcFlowOrchestrator;
import org.springframework.stereotype.Component;

/**
 * Where every OIDC IdP returns. A callback is the second leg of a flow some other handler
 * started — login or invitation acceptance — and which one is decided by what the start leg
 * stashed on the flow session, so these routes deliberately live apart from the login,
 * signup, and invite handlers that start flows.
 *
 * <p>These URLs are registered as redirect URIs in external IdPs (Google, Microsoft,
 * customer SSO), so changing them requires updating every registration. Flow-starting
 * handlers must build redirect URIs through this class's {@code *CallbackUrl} methods —
 * the IdP requires the exchange-time redirect_uri to equal the start-time one exactly.
 *
 * <ul>
 *   <li>{@code GET /api/oidc/callback/social/:configId} — platform social providers
 *       (org-tier login and org-invite acceptance).</li>
 *   <li>{@code GET /api/oidc/callback/sso/:configId} — an organization's own SSO provider
 *       (org-tier login and org-invite acceptance). Config lookup is scoped by the orgId
 *       stashed on the flow session — the pre-auth callback has no participant bound.</li>
 *   <li>{@code GET /api/app/:orgId/:appId/oidc/callback/:configId} — an application's
 *       providers (app-tier login and app-invite acceptance), scoped by the path ids.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OidcCallbackHandler {

    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final AuthEndpointSupport authEndpointSupport;
    private final InviteAcceptSupport inviteAcceptSupport;
    private final IamUserService iamUserService;
    private final OidcConfigurationRepository oidcConfigurationRepository;
    private final OrgSignupOidcConfigurationService orgSignupOidcConfigurationService;

    public void mountRoutes(Router router) {
        router.get("/api/oidc/callback/social/:configId").handler(this::handleSocialCallback);
        router.get("/api/oidc/callback/sso/:configId").handler(this::handleSsoCallback);
        router.get("/api/app/:orgId/:appId/oidc/callback/:configId").handler(this::handleAppCallback);
    }

    // ── Redirect-URI builders (single source of truth for flow starters) ──────

    public String socialCallbackUrl(String configId) {
        return authEndpointSupport.absoluteUrl("/api/oidc/callback/social/" + configId);
    }

    public String ssoCallbackUrl(String configId) {
        return authEndpointSupport.absoluteUrl("/api/oidc/callback/sso/" + configId);
    }

    public String appCallbackUrl(String orgId, String appId, String configId) {
        return authEndpointSupport.absoluteUrl("/api/app/" + orgId + "/" + appId + "/oidc/callback/" + configId);
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────

    private void handleSocialCallback(RoutingContext ctx) {
        String pathConfigId = ctx.pathParam("configId");

        oidcFlowOrchestrator.handleCallback(ctx,
                                            pathConfigId,
                                            socialCallbackUrl(pathConfigId),
                                            _ -> orgSignupOidcConfigurationService.findById(pathConfigId))
                            .onSuccess(result -> completeSocial(ctx, result))
                            .onFailure(ex -> authEndpointSupport.redirectCallbackFailure(ctx, ex));
    }

    private void completeSocial(RoutingContext ctx, CallbackResult<OrgSignupOidcConfiguration> result) {
        if (result.inviteToken() != null) {
            inviteAcceptSupport.completeOrgInviteAccept(ctx, result);
            return;
        }
        authEndpointSupport.completeOidcLogin(ctx, result.config(), result.claims(),
                sub -> iamUserService.findOrgUserByOidcIdentity(sub, result.config().getId()));
    }

    private void handleSsoCallback(RoutingContext ctx) {
        String pathConfigId = ctx.pathParam("configId");

        // The configId is trusted — it came from the IdP redirect we issued ourselves.
        oidcFlowOrchestrator.handleCallback(ctx,
                                            pathConfigId,
                                            ssoCallbackUrl(pathConfigId),
                                            orgId -> oidcConfigurationRepository.findById(pathConfigId, orgId))
                            .onSuccess(result -> completeSso(ctx, result))
                            .onFailure(ex -> authEndpointSupport.redirectCallbackFailure(ctx, ex));
    }

    private void completeSso(RoutingContext ctx, CallbackResult<OidcConfiguration> result) {
        if (result.inviteToken() != null) {
            inviteAcceptSupport.completeOrgInviteAccept(ctx, result);
            return;
        }
        authEndpointSupport.completeOidcLogin(ctx, result.config(), result.claims(),
                sub -> iamUserService.findByOidcIdentity(sub, result.config().getId(), result.orgId(), null));
    }

    private void handleAppCallback(RoutingContext ctx) {
        String orgId = ctx.pathParam("orgId");
        String appId = ctx.pathParam("appId");
        String pathConfigId = ctx.pathParam("configId");

        oidcFlowOrchestrator.<OidcConfiguration>handleCallback(
                ctx, pathConfigId, appCallbackUrl(orgId, appId, pathConfigId),
                _ -> oidcConfigurationRepository.findById(pathConfigId, orgId))
                .onSuccess(result -> completeApp(ctx, result, orgId, appId))
                .onFailure(ex -> authEndpointSupport.redirectCallbackFailure(ctx, ex));
    }

    private void completeApp(RoutingContext ctx,
                             CallbackResult<OidcConfiguration> result,
                             String orgId,
                             String appId) {
        if (result.inviteToken() != null) {
            inviteAcceptSupport.completeAppInviteAccept(ctx, result, orgId, appId);
            return;
        }
        authEndpointSupport.completeOidcLogin(ctx, result.config(), result.claims(),
                sub -> iamUserService.findByOidcIdentity(sub, result.config().getId(), orgId, appId));
    }
}
