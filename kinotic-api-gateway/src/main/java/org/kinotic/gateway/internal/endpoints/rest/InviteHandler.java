package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.iam.BaseOidcConfiguration;
import org.kinotic.domain.api.model.iam.PendingInvite;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.iam.InviteEmailMismatchException;
import org.kinotic.domain.api.services.iam.InviteService;
import org.kinotic.domain.api.services.iam.OrgSignupOidcConfigurationService;
import org.kinotic.domain.internal.api.repositories.ApplicationRepository;
import org.kinotic.domain.internal.api.repositories.OidcConfigurationRepository;
import org.kinotic.gateway.internal.endpoints.rest.support.AuthEndpointSupport;
import org.kinotic.gateway.internal.endpoints.rest.support.CallbackResult;
import org.kinotic.gateway.internal.endpoints.rest.support.OAuth2Util;
import org.kinotic.gateway.internal.endpoints.rest.support.OidcCallbackException;
import org.kinotic.gateway.internal.endpoints.rest.support.OidcFlowOrchestrator;
import org.kinotic.os.api.services.iam.OidcConfigurationService;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
public class InviteHandler {

    /** Frontend path of the unauthenticated invitation-accept page. */
    private static final String INVITE_ACCEPT_PATH = "/invite/accept";

    private final InviteService inviteService;
    private final OrganizationService organizationService;
    private final ApplicationRepository applicationRepository;
    private final OidcConfigurationService oidcConfigurationService;
    private final OidcConfigurationRepository oidcConfigurationRepository;
    private final OrgSignupOidcConfigurationService orgSignupOidcConfigurationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final AuthEndpointSupport authEndpointSupport;

    public void mountRoutes(Router router) {
        router.get("/api/invite").handler(this::handleDetails);
        router.post("/api/invite/accept").handler(this::handleLocalAccept);
        router.post("/api/invite/start/:configId").handler(this::handleOidcStart);
        router.get("/api/invite/callback/:configId").handler(this::handleOidcCallback);
    }

    /**
     * {@code GET /api/invite?token=} — invitation details plus the scope's live provider
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
                          resolveScopeProviders(invite));
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
     * {@code POST /api/invite/accept {token, password, displayName?}} — accept by setting a
     * password. Org invitees get a console session ({@code 204}); app invitees get a
     * confirmation payload and no session.
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
        String displayName = body == null ? null : body.getString("displayName");
        if (token == null || token.isBlank() || password == null || password.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "token and password are required");
            return;
        }

        Future.fromCompletionStage(inviteService.acceptLocalInvite(token, password, displayName))
              .onSuccess(user -> {
                  if (user.getApplicationId() != null) {
                      // An app-scope user is not a console user — confirm without a session.
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
     * {@code POST /api/invite/start/:configId} (form, {@code token}) — begins an OIDC accept
     * with one of the providers offered by {@link #handleDetails}, redirecting to the IdP.
     */
    private void handleOidcStart(RoutingContext ctx) {
        String configId = ctx.pathParam("configId");
        String formToken = ctx.request().getFormAttribute("token");
        String token = formToken != null && !formToken.isBlank() ? formToken : ctx.request().getParam("token");
        if (token == null || token.isBlank()) {
            redirectInviteError(ctx, OidcConstants.ERR_INVITE_INVALID, null);
            return;
        }

        Future.fromCompletionStage(inviteService.getValidInvite(token))
              .compose(invite -> startFlowForInvite(ctx, invite, configId, token))
              .onSuccess(url -> ctx.response().setStatusCode(302).putHeader("Location", url).end())
              .onFailure(err -> {
                  Throwable cause = err.getCause() != null ? err.getCause() : err;
                  log.warn("Invite OIDC start failed for config {}: {}", configId, cause.getMessage());
                  String code = cause instanceof OidcCallbackException oce
                          ? oce.getErrorCode() : OidcConstants.ERR_INVITE_INVALID;
                  redirectInviteError(ctx, code, token);
              });
    }

    /**
     * {@code GET /api/invite/callback/:configId} — the IdP returns here for every invite
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
                            ? oce.getErrorCode() : OidcConstants.ERR_EXCHANGE_FAILED;
                    redirectInviteError(ctx, code, null);
                });
    }

    /**
     * The post-IdP completion: validates the identity with the same prologue as login
     * (sub present, email verified), creates the member via the domain service (which
     * enforces the invited-email match), then routes by the invite's stored scope — org
     * invitees get a console session and land in the SPA, app invitees are sent to the
     * accept page's confirmation state without a session.
     */
    private void completeOidcAccept(RoutingContext ctx, CallbackResult<BaseOidcConfiguration> result) {
        String token = result.inviteToken();
        Map<String, Object> claims = result.claims();

        if (token == null) {
            // Only the start route above targets this callback, and it always stashes a token.
            redirectInviteError(ctx, OidcConstants.ERR_INVITE_INVALID, null);
            return;
        }
        String sub = OAuth2Util.stringClaim(claims, "sub");
        String email = OAuth2Util.stringClaim(claims, "email");
        if (sub == null || email == null) {
            redirectInviteError(ctx, OidcConstants.ERR_INVALID_TOKEN, token);
            return;
        }
        if (!OAuth2Util.isEmailVerified(claims, result.config().getProvider())) {
            redirectInviteError(ctx, OidcConstants.ERR_EMAIL_NOT_VERIFIED, token);
            return;
        }

        Future.fromCompletionStage(inviteService.acceptOidcInvite(token, sub, result.config().getId(), email))
              .onSuccess(user -> {
                  if (user.getApplicationId() != null) {
                      // An app-scope user is not a console user — confirm without a session.
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
                      redirectInviteError(ctx, OidcConstants.ERR_EMAIL_MISMATCH, token);
                  } else {
                      log.warn("Invite acceptance failed: {}", cause.getMessage());
                      redirectInviteError(ctx, OidcConstants.ERR_INVITE_INVALID, null);
                  }
              });
    }

    /**
     * Starts the OIDC flow for the chosen config, resolved strictly within the invite's
     * scope's allowed provider set, returning to this handler's own callback.
     */
    private Future<String> startFlowForInvite(RoutingContext ctx, PendingInvite invite, String configId, String token) {
        String orgId = invite.getOrganizationId();

        if (invite.getApplicationId() != null) {
            String appId = invite.getApplicationId();
            return Future.fromCompletionStage(applicationRepository.findById(appId, orgId))
                         .compose(app -> {
                             if (app == null || app.getOidcConfigurationIds() == null
                                     || !app.getOidcConfigurationIds().contains(configId)) {
                                 return Future.failedFuture(new OidcCallbackException(OidcConstants.ERR_CONFIG_NOT_FOUND));
                             }
                             return Future.fromCompletionStage(
                                     oidcConfigurationService.findEnabledByIds(List.of(configId), orgId));
                         })
                         .compose(configs -> configs.isEmpty()
                                 ? Future.failedFuture(new OidcCallbackException(OidcConstants.ERR_CONFIG_NOT_FOUND))
                                 : oidcFlowOrchestrator.startFlow(ctx, configs.getFirst(),
                                                                  inviteCallbackUrl(configId), orgId, token));
        }

        return Future.fromCompletionStage(orgSignupOidcConfigurationService.findById(configId))
                     .compose(social -> {
                         if (social != null && social.isEnabled()) {
                             return oidcFlowOrchestrator.startFlow(ctx, social,
                                                                   inviteCallbackUrl(configId), orgId, token);
                         }
                         return Future.fromCompletionStage(oidcConfigurationService.findOrgLoginConfig(orgId))
                                      .compose(sso -> {
                                          if (sso == null || !sso.isEnabled() || !sso.getId().equals(configId)) {
                                              return Future.failedFuture(
                                                      new OidcCallbackException(OidcConstants.ERR_CONFIG_NOT_FOUND));
                                          }
                                          return oidcFlowOrchestrator.startFlow(ctx, sso,
                                                                                inviteCallbackUrl(configId), orgId, token);
                                      });
                     });
    }

    /**
     * The providers the invitee may accept with, resolved from the scope's live config at
     * request time: org invites offer the platform social configs plus the org's enabled
     * SSO config; app invites offer the application's enabled configs.
     */
    private CompletableFuture<List<BaseOidcConfiguration>> resolveScopeProviders(PendingInvite invite) {
        String orgId = invite.getOrganizationId();
        if (invite.getApplicationId() != null) {
            return applicationRepository.findById(invite.getApplicationId(), orgId)
                    .thenCompose(app -> {
                        if (app == null || app.getOidcConfigurationIds() == null
                                || app.getOidcConfigurationIds().isEmpty()) {
                            return CompletableFuture.completedFuture(List.of());
                        }
                        return oidcConfigurationService.findEnabledByIds(app.getOidcConfigurationIds(), orgId);
                    })
                    .thenApply(ArrayList::new);
        }
        return orgSignupOidcConfigurationService.findAllEnabled()
                .thenCombine(oidcConfigurationService.findOrgLoginConfig(orgId),
                             (social, sso) -> {
                                 List<BaseOidcConfiguration> providers = new ArrayList<>(social);
                                 if (sso != null && sso.isEnabled()) {
                                     providers.add(sso);
                                 }
                                 return providers;
                             });
    }

    /**
     * Resolves the callback's config, which may live in either table: platform social
     * configs (unscoped) or org SSO / app configs (org-scoped by the flow session's orgId).
     */
    private CompletableFuture<BaseOidcConfiguration> resolveCallbackConfig(String configId, String orgId) {
        // Searching both tables by bare id is safe here: the orchestrator only calls this
        // resolver after matching the path configId against the flow session, and the
        // session value was written by handleOidcStart, which vets the config against the
        // invite's allowed provider set. So this can only ever load the row the start leg
        // already approved (ids are per-row UUIDs — no cross-table collisions).
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
        return authEndpointSupport.absoluteUrl("/api/invite/callback/" + configId);
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
