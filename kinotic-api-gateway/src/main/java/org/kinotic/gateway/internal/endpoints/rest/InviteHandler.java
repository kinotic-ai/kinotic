package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.iam.AuthType;
import org.kinotic.domain.api.model.iam.OidcConfiguration;
import org.kinotic.domain.api.model.iam.PendingInvite;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.iam.InviteService;
import org.kinotic.domain.internal.api.repositories.OidcConfigurationRepository;
import org.kinotic.gateway.internal.endpoints.rest.support.AuthEndpointSupport;
import org.kinotic.gateway.internal.endpoints.rest.support.CallbackResult;
import org.kinotic.gateway.internal.endpoints.rest.support.OAuth2Util;
import org.kinotic.gateway.internal.endpoints.rest.support.OidcFlowOrchestrator;
import org.springframework.stereotype.Component;

/**
 * Member-invitation acceptance routes — the invite counterpart to {@link OrganizationSignupHandler}.
 * Unlike sign-up, accepting an invitation creates an {@link org.kinotic.domain.api.model.iam.IamUser}
 * in an <em>existing</em> scope carried on the {@link PendingInvite}; the OIDC leg reuses the shared
 * {@link OidcFlowOrchestrator} and {@link AuthEndpointSupport#completeOidcLogin} machinery, supplying
 * a lookup that creates the member instead of looking one up.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InviteHandler {

    private final InviteService inviteService;
    private final OrganizationService organizationService;
    private final OidcConfigurationRepository oidcConfigurationRepository;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final AuthEndpointSupport authEndpointSupport;

    public void mountRoutes(Router router) {
        router.get("/api/invite").handler(this::handleGetInvite);
        router.post("/api/invite/accept").handler(this::handleLocalAccept);
        router.post("/api/invite/start").handler(this::handleOidcStart);
        router.get("/api/invite/callback/:configId").handler(this::handleOidcCallback);
    }

    /**
     * {@code GET /api/invite?token=…} — returns the invitation's display details (email, org name,
     * auth method, provider name) so the acceptance page can render. {@code 400} if invalid/expired.
     */
    private void handleGetInvite(RoutingContext ctx) {
        String token = ctx.request().getParam("token");
        if (token == null || token.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "token is required");
            return;
        }
        Future.fromCompletionStage(inviteService.getValidInvite(token))
              .compose(invite -> Future.fromCompletionStage(organizationService.findById(invite.getOrganizationId()))
                      .compose(org -> resolveProviderName(invite)
                              .map(providerName -> new JsonObject()
                                      .put("email", invite.getEmail())
                                      .put("displayName", invite.getDisplayName())
                                      .put("organizationName", org != null ? org.getName() : null)
                                      .put("authType", invite.getAuthType().name())
                                      .put("oidcProviderName", providerName))))
              .onSuccess(json -> ctx.response().putHeader("Content-Type", "application/json").end(json.encode()))
              .onFailure(ex -> {
                  Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                  authEndpointSupport.respondError(ctx, 400, cause.getMessage());
              });
    }

    /**
     * {@code POST /api/invite/accept {token, password}} — completes a LOCAL invitation: creates the
     * member with the chosen password and establishes the browser session.
     */
    private void handleLocalAccept(RoutingContext ctx) {
        JsonObject body;
        try {
            body = ctx.body().asJsonObject();
        } catch (Exception e) {
            authEndpointSupport.respondError(ctx, 400, "Invalid request body");
            return;
        }
        String token = body == null ? null : body.getString("token");
        String password = body == null ? null : body.getString("password");
        if (token == null || token.isBlank() || password == null || password.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "token and password are required");
            return;
        }
        Future.fromCompletionStage(inviteService.acceptLocalInvite(token, password))
              .onSuccess(user -> authEndpointSupport.respondSuccess(ctx, user))
              .onFailure(ex -> {
                  Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                  log.warn("Invite acceptance failed: {}", cause.getMessage());
                  authEndpointSupport.respondError(ctx, 400, cause.getMessage());
              });
    }

    /**
     * {@code POST /api/invite/start {token}} — begins the OIDC leg of an invitation: resolves the
     * invitation's OIDC config and returns the IdP authorization URL for the browser to navigate to.
     * The invitation token is stashed on the flow session so the callback can complete it.
     */
    private void handleOidcStart(RoutingContext ctx) {
        JsonObject body;
        try {
            body = ctx.body().asJsonObject();
        } catch (Exception e) {
            authEndpointSupport.respondError(ctx, 400, "Invalid request body");
            return;
        }
        String token = body == null ? null : body.getString("token");
        if (token == null || token.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "token is required");
            return;
        }
        Future.fromCompletionStage(inviteService.getValidInvite(token))
              .compose(invite -> {
                  if (invite.getAuthType() != AuthType.OIDC || invite.getOidcConfigId() == null) {
                      return Future.failedFuture(new IllegalArgumentException("This invitation is not an SSO invitation."));
                  }
                  return Future.fromCompletionStage(
                                  oidcConfigurationRepository.findById(invite.getOidcConfigId(), invite.getOrganizationId()))
                          .compose(config -> {
                              if (config == null || !config.isEnabled()) {
                                  return Future.failedFuture(new IllegalArgumentException(
                                          "The SSO provider for this invitation is unavailable."));
                              }
                              return oidcFlowOrchestrator.startFlow(ctx, config, callbackUrl(config.getId()),
                                      invite.getOrganizationId(), invite.getVerificationToken());
                          });
              })
              .onSuccess(url -> ctx.response().putHeader("Content-Type", "application/json")
                      .end(new JsonObject().put("redirect", url).encode()))
              .onFailure(ex -> {
                  Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                  log.warn("Invite SSO start failed: {}", cause.getMessage());
                  authEndpointSupport.respondError(ctx, 400, cause.getMessage());
              });
    }

    /**
     * {@code GET /api/invite/callback/:configId} — the invitation's OIDC IdP returns here. Validates
     * the callback via {@link OidcFlowOrchestrator}, then reuses
     * {@link AuthEndpointSupport#completeOidcLogin} with a lookup that creates the member for the
     * invitation's scope — asserting the IdP-verified email matches the invited email.
     */
    private void handleOidcCallback(RoutingContext ctx) {
        String pathConfigId = ctx.pathParam("configId");
        oidcFlowOrchestrator.handleCallback(ctx, pathConfigId, callbackUrl(pathConfigId),
                        orgId -> oidcConfigurationRepository.findById(pathConfigId, orgId))
                .onSuccess(result -> completeOidcInvite(ctx, result))
                .onFailure(ex -> authEndpointSupport.redirectCallbackFailure(ctx, ex));
    }

    private void completeOidcInvite(RoutingContext ctx, CallbackResult<OidcConfiguration> result) {
        String inviteToken = result.inviteToken();
        String verifiedEmail = OAuth2Util.stringClaim(result.claims(), "email");
        if (inviteToken == null || verifiedEmail == null) {
            authEndpointSupport.redirectError(ctx, OidcConstants.ERR_INVALID_TOKEN);
            return;
        }
        authEndpointSupport.completeOidcLogin(ctx, result.config(), result.claims(),
                sub -> inviteService.acceptOidcInvite(inviteToken, sub, result.config().getId(), verifiedEmail));
    }

    private Future<String> resolveProviderName(PendingInvite invite) {
        if (invite.getAuthType() != AuthType.OIDC || invite.getOidcConfigId() == null) {
            return Future.succeededFuture(null);
        }
        return Future.fromCompletionStage(
                        oidcConfigurationRepository.findById(invite.getOidcConfigId(), invite.getOrganizationId()))
                .map(config -> config != null ? config.getName() : null);
    }

    private String callbackUrl(String configId) {
        return authEndpointSupport.absoluteUrl("/api/invite/callback/" + configId);
    }
}
