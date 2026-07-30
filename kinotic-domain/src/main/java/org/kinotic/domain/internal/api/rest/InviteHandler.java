package org.kinotic.domain.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.iam.BaseOidcConfiguration;
import org.kinotic.domain.api.model.iam.PendingInvite;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.exceptions.InviteEmailMismatchException;
import org.kinotic.domain.api.services.iam.InviteService;
import org.kinotic.domain.api.services.iam.OrgSignupOidcConfigurationService;
import org.kinotic.domain.internal.api.repositories.OidcConfigurationRepository;
import org.kinotic.domain.internal.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.internal.api.rest.support.CallbackResult;
import org.kinotic.domain.internal.api.rest.support.OAuth2Util;
import org.kinotic.domain.internal.api.rest.support.OidcCallbackException;
import org.kinotic.domain.internal.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.services.iam.OidcConfigurationService;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Unauthenticated invitation-accept routes — the complete invite flow, self-contained. The
 * invitee arrives from the emailed accept link and accepts with a password or any OIDC
 * provider configured for the target scope; OIDC flows return to this handler's own
 * callback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InviteHandler implements SuppliesGatewayRoutes {

    /** Frontend path of the unauthenticated invitation-accept page. */
    private static final String INVITE_ACCEPT_PATH = "/invite/accept";

    private final InviteService inviteService;
    private final OrganizationService organizationService;
    private final OidcConfigurationService oidcConfigurationService;
    private final OidcConfigurationRepository oidcConfigurationRepository;
    private final OrgSignupOidcConfigurationService orgSignupOidcConfigurationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final AuthEndpointSupport authEndpointSupport;

    @Override
    public void mountRoutes(Router router) {
        router.get("/api/auth/invite/details").handler(this::handleDetails);
        router.post("/api/auth/invite/accept").handler(this::handleLocalAccept);
        router.post("/api/auth/invite/oidc/start/:configId").handler(this::handleOidcStart);
        router.get("/api/auth/invite/oidc/callback/:configId").handler(this::handleOidcCallback);
    }

    /**
     * {@code GET /api/auth/invite/details?token=} — invitation details plus the scope's live provider
     * list, for rendering the accept page.
     */
    private void handleDetails(RoutingContext ctx) {
        String token = ctx.request().getParam("token");
        if (token == null || token.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "token is required");
            return;
        }

        Future.fromCompletionStage(inviteService.getValidInvite(token))
              .compose(invite -> {
                  Future<String> orgName = Future.fromCompletionStage(
                          organizationService.findById(invite.getOrganizationId()))
                          .map(org -> org == null ? null : org.getName());
                  Future<List<BaseOidcConfiguration>> providers = Future.fromCompletionStage(
                          oidcConfigurationService.findEnabledForScope(invite.getOrganizationId(),
                                                                      invite.getApplicationId()));
                  return Future.all(orgName, providers)
                               .map(v -> new JsonObject()
                                       .put("email", invite.getEmail())
                                       .put("displayName", invite.getDisplayName())
                                       .put("organizationName", orgName.result())
                                       .put("applicationId", invite.getApplicationId())
                                       .put("invitedByName", invite.getInvitedByName())
                                       .put("expiresAt", invite.getExpiresAt().toInstant())
                                       .put("localEnabled", true)
                                       .put("providers", authEndpointSupport.providersJson(providers.result())));
              })
              .onSuccess(json -> ctx.response().putHeader("Content-Type", "application/json").end(json.encode()))
              .onFailure(err -> respondInviteFailure(ctx, err, "Failed to load invitation"));
    }

    /**
     * {@code POST /api/auth/invite/accept {token, password, displayName?}} — accept by setting a
     * password. Both scopes get a browser session, same as logging in. Org invitees get a
     * {@code 204}; app invitees get a payload identifying the application, which the accept
     * page uses for its confirmation state since the web app is not their UI.
     */
    private void handleLocalAccept(RoutingContext ctx) {
        JsonObject body = authEndpointSupport.readJsonBody(ctx);
        if (body == null) {
            return;
        }
        String token = body.getString("token");
        String password = body.getString("password");
        String displayName = body.getString("displayName");
        if (token == null || token.isBlank() || password == null || password.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "token and password are required");
            return;
        }

        Future.fromCompletionStage(inviteService.acceptLocalInvite(token, password, displayName))
              .onSuccess(user -> {
                  if (user.getApplicationId() != null) {
                      // Session established like any login (ApplicationParticipant); the payload
                      // tells the accept page which application to point the invitee at.
                      authEndpointSupport.establishSession(ctx, user);
                      ctx.response().putHeader("Content-Type", "application/json")
                         .end(new JsonObject()
                                 .put("scope", "APPLICATION")
                                 .put("organizationId", user.getOrganizationId())
                                 .put("applicationId", user.getApplicationId())
                                 .encode());
                  } else {
                      authEndpointSupport.respondSuccess(ctx, user);
                  }
              })
              .onFailure(err -> respondInviteFailure(ctx, err, "Failed to accept invitation"));
    }

    /**
     * {@code POST /api/auth/invite/oidc/start/:configId} (form, {@code token}) — begins an OIDC accept
     * with one of the providers offered by {@link #handleDetails}, redirecting to the IdP.
     */
    private void handleOidcStart(RoutingContext ctx) {
        String configId = ctx.pathParam("configId");
        String formToken = ctx.request().getFormAttribute("token");
        String token = formToken != null && !formToken.isBlank() ? formToken : ctx.request().getParam("token");
        if (token == null || token.isBlank()) {
            redirectInviteError(ctx, OidcErrorCodes.INVITE_INVALID, null);
            return;
        }

        Future.fromCompletionStage(inviteService.getValidInvite(token))
              .compose(invite -> startFlowForInvite(ctx, invite, configId, token))
              .onSuccess(url -> ctx.response().setStatusCode(302).putHeader("Location", url).end())
              .onFailure(err -> {
                  Throwable cause = err.getCause() != null ? err.getCause() : err;
                  log.warn("Invite OIDC start failed for config {}: {}", configId, cause.getMessage());
                  String code = cause instanceof OidcCallbackException oce
                          ? oce.getErrorCode() : OidcErrorCodes.INVITE_INVALID;
                  redirectInviteError(ctx, code, token);
              });
    }

    /**
     * {@code GET /api/auth/invite/oidc/callback/:configId} — the IdP returns here for every invite
     * acceptance; the accept token was stashed on the flow session at start.
     */
    private void handleOidcCallback(RoutingContext ctx) {
        String pathConfigId = ctx.pathParam("configId");

        oidcFlowOrchestrator.<BaseOidcConfiguration>handleCallback(
                ctx, pathConfigId, inviteCallbackUrl(pathConfigId),
                orgId -> resolveCallbackConfig(pathConfigId, orgId))
                .onSuccess(result -> completeOidcAccept(ctx, result))
                .onFailure(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String code = cause instanceof OidcCallbackException oce
                            ? oce.getErrorCode() : OidcErrorCodes.EXCHANGE_FAILED;
                    redirectInviteError(ctx, code, null);
                });
    }

    /**
     * Finishes an OIDC invitation acceptance after the browser returns from the identity
     * provider. How we got here: the invitee clicked a provider button on the accept page,
     * {@link #handleOidcStart} checked their invite token and redirected them to the IdP to
     * sign in, and the IdP sent them back to {@link #handleOidcCallback}, which verified
     * the OIDC exchange and recovered the invite token from the flow session.
     *
     * <p>Three steps remain. First, confirm the IdP gave us a usable identity — a subject
     * id and a verified email (the same checks a normal OIDC login performs). Second, ask
     * {@link InviteService#acceptOidcInvite} to create the member; it also rejects the
     * acceptance when the IdP-verified email is not the email that was invited. Third,
     * establish the browser session (same as logging in — the participant type follows the
     * member's scope) and send the browser onward: an organization member lands in the web
     * app; an application member is sent to the accept page's confirmation state, since the
     * web app is not their UI.
     */
    private void completeOidcAccept(RoutingContext ctx, CallbackResult<BaseOidcConfiguration> result) {
        String token = result.inviteToken();
        Map<String, Object> claims = result.claims();

        if (token == null) {
            // Only the start route above targets this callback, and it always stashes a token.
            redirectInviteError(ctx, OidcErrorCodes.INVITE_INVALID, null);
            return;
        }
        String sub = OAuth2Util.stringClaim(claims, "sub");
        String email = OAuth2Util.stringClaim(claims, "email");
        if (sub == null || email == null) {
            redirectInviteError(ctx, OidcErrorCodes.INVALID_TOKEN, token);
            return;
        }
        if (!OAuth2Util.isEmailVerified(claims, result.config().getProvider())) {
            redirectInviteError(ctx, OidcErrorCodes.EMAIL_NOT_VERIFIED, token);
            return;
        }

        Future.fromCompletionStage(inviteService.acceptOidcInvite(token, sub, result.config().getId(), email))
              .onSuccess(user -> {
                  if (user.getApplicationId() != null) {
                      // Session established like any login (ApplicationParticipant); the redirect
                      // shows the confirmation state since the web app is not an app user's UI.
                      authEndpointSupport.establishSession(ctx, user);
                      ctx.response().setStatusCode(302)
                         .putHeader("Location", authEndpointSupport.appUrl(
                                 INVITE_ACCEPT_PATH + "?accepted=app&application="
                                         + URLEncoder.encode(user.getApplicationId(), StandardCharsets.UTF_8)))
                         .end();
                  } else {
                      authEndpointSupport.redirectSuccess(ctx, user);
                  }
              })
              .onFailure(err -> {
                  Throwable cause = err.getCause() != null ? err.getCause() : err;
                  if (cause instanceof InviteEmailMismatchException) {
                      // The invite is NOT consumed on mismatch — keep the token so the
                      // invitee can retry with another provider or a password.
                      redirectInviteError(ctx, OidcErrorCodes.EMAIL_MISMATCH, token);
                  } else {
                      log.warn("Invite acceptance failed: {}", cause.getMessage());
                      redirectInviteError(ctx, OidcErrorCodes.INVITE_INVALID, null);
                  }
              });
    }

    /**
     * Builds the IdP authorization redirect for the provider the invitee chose. The chosen
     * configId must be one the invite's target scope offers, which is the same list
     * {@link #handleDetails} rendered; anything else fails with config_not_found. The flow
     * carries the accept token on the session and returns to {@link #handleOidcCallback}.
     */
    private Future<String> startFlowForInvite(RoutingContext ctx, PendingInvite invite, String configId, String token) {
        return Future.fromCompletionStage(
                             oidcConfigurationService.findEnabledForScope(invite.getOrganizationId(),
                                                                         invite.getApplicationId()))
                     .compose(offered -> {
                         BaseOidcConfiguration chosen = offered.stream()
                                                              .filter(c -> configId.equals(c.getId()))
                                                              .findFirst()
                                                              .orElse(null);
                         Future<String> ret;
                         if (chosen == null) {
                             ret = Future.failedFuture(new OidcCallbackException(OidcErrorCodes.CONFIG_NOT_FOUND));
                         } else {
                             ret = oidcFlowOrchestrator.startFlow(ctx, chosen, inviteCallbackUrl(configId),
                                                                  invite.getOrganizationId(), token);
                         }
                         return ret;
                     });
    }

    /**
     * Resolves the callback's config, which may live in either table: platform social
     * configs (unscoped) or org SSO / app configs (org-scoped by the flow session's orgId).
     */
    private CompletableFuture<BaseOidcConfiguration> resolveCallbackConfig(String configId, String orgId) {
        // Searching both tables by bare id is safe: we only get here after the orchestrator
        // matched the path's configId against the flow session, and that session value was
        // written by handleOidcStart, which only accepts configs the invite's target scope
        // offers. So whatever this finds is a config the start leg already approved (ids
        // are per-row UUIDs, so the same id cannot exist in both tables).
        return orgSignupOidcConfigurationService.findById(configId)
                .thenCompose(social -> {
                    if (social != null) {
                        return CompletableFuture.<BaseOidcConfiguration>completedFuture(social);
                    }
                    // Invite flows always stash an orgId; this guard only turns a corrupted
                    // session into config_not_found instead of a repository exception.
                    if (orgId == null) {
                        return CompletableFuture.completedFuture(null);
                    }
                    // The cast widens the future's element type — CompletableFuture is invariant.
                    return oidcConfigurationRepository.findById(configId, orgId)
                                                      .thenApply(c -> (BaseOidcConfiguration) c);
                });
    }

    private String inviteCallbackUrl(String configId) {
        return authEndpointSupport.absoluteUrl("/api/auth/invite/oidc/callback/" + configId);
    }

    /**
     * {@code 302 Location: <appBaseUrl>/invite/accept?error=<code>}. A non-null token is
     * kept in the URL so the page can reload the invitation and let the invitee try
     * another method.
     */
    private void redirectInviteError(RoutingContext ctx, String errorCode, String token) {
        String location = authEndpointSupport.appUrl(INVITE_ACCEPT_PATH)
                + "?error=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8);
        if (token != null) {
            location += "&token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        }
        ctx.response().setStatusCode(302).putHeader("Location", location).end();
    }

    /**
     * Invalid/expired tokens raise IllegalArgumentException with invitee-friendly messages;
     * surface those as 400s and keep everything else generic.
     */
    private void respondInviteFailure(RoutingContext ctx, Throwable err, String genericMessage) {
        Throwable cause = err.getCause() != null ? err.getCause() : err;
        if (cause instanceof IllegalArgumentException) {
            authEndpointSupport.respondError(ctx, 400, cause.getMessage());
        } else {
            log.warn("{}: {}", genericMessage, cause.getMessage());
            authEndpointSupport.respondError(ctx, 500, genericMessage);
        }
    }
}
