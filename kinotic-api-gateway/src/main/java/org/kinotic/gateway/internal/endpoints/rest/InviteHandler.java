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
import org.kinotic.domain.api.services.iam.InviteService;
import org.kinotic.domain.api.services.iam.OrgSignupOidcConfigurationService;
import org.kinotic.domain.internal.api.repositories.ApplicationRepository;
import org.kinotic.gateway.internal.endpoints.rest.support.AuthEndpointSupport;
import org.kinotic.gateway.internal.endpoints.rest.support.InviteAcceptSupport;
import org.kinotic.gateway.internal.endpoints.rest.support.OidcCallbackException;
import org.kinotic.gateway.internal.endpoints.rest.support.OidcFlowOrchestrator;
import org.kinotic.os.api.services.iam.OidcConfigurationService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Unauthenticated invitation-accept routes. The invitee arrives from the emailed accept
 * link; the page these routes serve shows who invited them and into what, then lets them
 * accept with a password or any OIDC provider configured for the target scope.
 *
 * <ul>
 *   <li>{@code GET /api/invite?token=} — invitation details + the scope's live provider
 *       list for the accept page.</li>
 *   <li>{@code POST /api/invite/accept {token, password, displayName?}} — accept by
 *       setting a password. Org invitees get a console session; app invitees get a
 *       confirmation payload and no session.</li>
 *   <li>{@code POST /api/invite/start/:configId} (form, {@code token}) — begins an OIDC
 *       accept. The chosen config must be in the invite's scope's allowed set; the IdP
 *       returns to the matching <em>existing</em> callback in {@link OidcCallbackHandler}
 *       with the token riding the flow session, so no new redirect URI is ever registered
 *       with an IdP.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InviteHandler {

    private final InviteService inviteService;
    private final OrganizationService organizationService;
    private final ApplicationRepository applicationRepository;
    private final OidcConfigurationService oidcConfigurationService;
    private final OrgSignupOidcConfigurationService orgSignupOidcConfigurationService;
    private final OidcCallbackHandler oidcCallbackHandler;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final AuthEndpointSupport authEndpointSupport;
    private final InviteAcceptSupport inviteAcceptSupport;

    public void mountRoutes(Router router) {
        router.get("/api/invite").handler(this::handleDetails);
        router.post("/api/invite/accept").handler(this::handleLocalAccept);
        router.post("/api/invite/start/:configId").handler(this::handleOidcStart);
    }

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

    private void handleOidcStart(RoutingContext ctx) {
        String configId = ctx.pathParam("configId");
        String formToken = ctx.request().getFormAttribute("token");
        String token = formToken != null && !formToken.isBlank() ? formToken : ctx.request().getParam("token");
        if (token == null || token.isBlank()) {
            inviteAcceptSupport.redirectInviteError(ctx, OidcConstants.ERR_INVITE_INVALID);
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
                  inviteAcceptSupport.redirectInviteError(ctx, code, token);
              });
    }

    /**
     * Starts the OIDC flow for the chosen config, resolved strictly within the invite's
     * scope's allowed provider set, targeting the matching existing callback in
     * {@link OidcCallbackHandler} — so no new redirect URI is ever registered with an IdP.
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
                                 : oidcFlowOrchestrator.startFlow(
                                         ctx, configs.getFirst(),
                                         oidcCallbackHandler.appCallbackUrl(orgId, appId, configId),
                                         orgId, token));
        }

        return Future.fromCompletionStage(orgSignupOidcConfigurationService.findById(configId))
                     .compose(social -> {
                         if (social != null && social.isEnabled()) {
                             return oidcFlowOrchestrator.startFlow(
                                     ctx, social,
                                     oidcCallbackHandler.socialCallbackUrl(configId),
                                     orgId, token);
                         }
                         return Future.fromCompletionStage(oidcConfigurationService.findOrgLoginConfig(orgId))
                                      .compose(sso -> {
                                          if (sso == null || !sso.isEnabled() || !sso.getId().equals(configId)) {
                                              return Future.failedFuture(
                                                      new OidcCallbackException(OidcConstants.ERR_CONFIG_NOT_FOUND));
                                          }
                                          return oidcFlowOrchestrator.startFlow(
                                                  ctx, sso,
                                                  oidcCallbackHandler.ssoCallbackUrl(configId),
                                                  orgId, token);
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
