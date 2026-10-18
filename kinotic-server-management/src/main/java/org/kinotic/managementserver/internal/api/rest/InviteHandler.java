package org.kinotic.managementserver.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.PendingInvite;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.exceptions.AccountDisabledException;
import org.kinotic.domain.api.exceptions.ExistingAccountException;
import org.kinotic.domain.api.exceptions.InviteEmailMismatchException;
import org.kinotic.domain.api.model.security.participant.ApplicationParticipant;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.rest.support.CallbackResult;
import org.kinotic.domain.api.rest.support.OAuth2Util;
import org.kinotic.domain.api.rest.support.OidcCallbackException;
import org.kinotic.domain.api.rest.support.OidcErrorCodes;
import org.kinotic.domain.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.rest.support.OidcFlowPurpose;
import org.kinotic.domain.api.rest.support.PendingFlow;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import org.kinotic.managementserver.api.config.ManagementServerProperties;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Unauthenticated invitation-accept routes. The invitee arrives from the emailed accept link and accepts with
 * a password or, for an invitation into an organization, through any provider the organization's login
 * offers; that flow returns to the configuration's own callback at {@link OrganizationOidcCallbackHandler},
 * which hands it back here to complete. An invitation into an application is accepted with a password: its
 * providers return to the app server, which holds no invitation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InviteHandler implements SuppliesGatewayRoutes, OidcFlowCompleter {

    /** Frontend path of the unauthenticated invitation-accept page. */
    private static final String INVITE_ACCEPT_PATH = "/invite/accept";

    private final InviteService inviteService;
    private final ParticipantIdentityService identityService;
    private final OrganizationService organizationService;
    private final OidcConfigurationService oidcConfigurationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final AuthEndpointSupport authEndpointSupport;
    private final ManagementServerProperties properties;

    @Override
    public void mountRoutes(Router router) {
        router.get("/api/auth/invite/details").handler(this::handleDetails);
        router.post("/api/auth/invite/accept").handler(this::handleLocalAccept);
        router.post("/api/auth/invite/oidc/start/:configId").handler(this::handleOidcStart);
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

        inviteService.getValidInvite(token)
              .compose(invite -> {
                  Future<String> orgName = organizationService.findById(invite.getOrganizationId())
                          .map(org -> org == null ? null : org.getName());
                  // an application's providers return to the app server, which holds no invitation, so an
                  // application invitee accepts with a password
                  Future<List<OidcConfiguration>> providers = invite.getApplicationId() != null
                          ? Future.succeededFuture(List.of())
                          : oidcConfigurationService.findEnabledForScope(invite.getOrganizationId(), null);
                  // the holder of the invitation owns the invited email, so telling them an account exists for it
                  // tells a stranger nothing; the page then asks for that account's password, or, when it has none,
                  // for the provider it is bound to, instead of a new password
                  Future<UserParticipantIdentity> account = invite.getApplicationId() != null
                          ? Future.succeededFuture()
                          : identityService.findByEmail(invite.getEmail(), null, null);
                  Future<Boolean> accountHasPassword = account.compose(found -> found == null
                          ? Future.succeededFuture(false)
                          : identityService.hasPassword(found.getId()));
                  return Future.all(orgName, providers, accountHasPassword)
                               .map(v -> new JsonObject()
                                       .put("email", invite.getEmail())
                                       .put("displayName", invite.getDisplayName())
                                       .put("organizationName", orgName.result())
                                       .put("applicationId", invite.getApplicationId())
                                       .put("invitedByName", invite.getInvitedByName())
                                       .put("expiresAt", invite.getExpiresAt().toInstant())
                                       .put("localEnabled", true)
                                       .put("accountExists", account.result() != null)
                                       .put("accountHasPassword", accountHasPassword.result())
                                       .put("providers", authEndpointSupport.providersJson(providers.result())));
              })
              .onSuccess(json -> ctx.response().putHeader("Content-Type", "application/json").end(json.encode()))
              .onFailure(err -> respondInviteFailure(ctx, err, "Failed to load invitation"));
    }

    /**
     * {@code POST /api/auth/invite/accept {token, password, displayName?}} — accept by setting a
     * password. Org invitees get a browser session, same as logging in, and a {@code 204}; app
     * invitees get a payload identifying the application, which the accept page uses for its
     * confirmation state, and sign in at the application's own UI.
     */
    private void handleLocalAccept(RoutingContext ctx) {
        JsonObject body = authEndpointSupport.readJsonBody(ctx);
        String token = body.getString("token");
        String password = body.getString("password");
        String displayName = body.getString("displayName");
        if (token == null || token.isBlank() || password == null || password.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "token and password are required");
            return;
        }

        inviteService.acceptLocalInvite(token, password, displayName)
              .onSuccess(member -> {
                  if (member instanceof ApplicationParticipant user) {
                      // the management server holds organization logins only, so an application member
                      // signs in at the application; the payload names it for the accept page
                      ctx.response().putHeader("Content-Type", "application/json")
                         .end(new JsonObject()
                                 .put("scope", "APPLICATION")
                                 .put("organizationId", user.getOrganizationId())
                                 .put("applicationId", user.getApplicationId())
                                 .encode());
                  } else {
                      authEndpointSupport.respondSuccess(ctx, member);
                  }
              })
              .onFailure(err -> {
                  if (err instanceof ExistingAccountException) {
                      // the invitation stays valid: the page asks for the existing account's password
                      authEndpointSupport.respondError(ctx, 401, err.getMessage());
                  } else if (err instanceof AccountDisabledException) {
                      authEndpointSupport.respondError(ctx, 403, err.getMessage());
                  } else {
                      respondInviteFailure(ctx, err, "Failed to accept invitation");
                  }
              });
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

        inviteService.getValidInvite(token)
              .compose(invite -> startFlowForInvite(ctx, invite, configId, token))
              .onSuccess(url -> ctx.response().setStatusCode(302).putHeader("Location", url).end())
              .onFailure(err -> {
                  log.warn("Invite OIDC start failed for config {}: {}", configId, err.getMessage());
                  String code = err instanceof OidcCallbackException oce
                          ? oce.getErrorCode() : OidcErrorCodes.INVITE_INVALID;
                  redirectInviteError(ctx, code, token);
              });
    }

    @Override
    public OidcFlowPurpose purpose() {
        return OidcFlowPurpose.INVITATION;
    }

    /** A callback that failed validation keeps the invitation, so the invitee can try another method. */
    @Override
    public void fail(RoutingContext ctx, PendingFlow flow, Throwable failure) {
        redirectInviteError(ctx, authEndpointSupport.callbackErrorCode(failure), flow.inviteToken());
    }

    /**
     * Finishes an OIDC invitation acceptance after the browser returns from the identity
     * provider. How we got here: the invitee clicked a provider button on the accept page,
     * {@link #handleOidcStart} checked their invite token and redirected them to the IdP to
     * sign in, and the IdP sent them back to the configuration's callback, which verified
     * the OIDC exchange and recovered the invite token from the flow session.
     *
     * <p>Three steps remain. First, confirm the IdP gave us a usable identity — a subject
     * id and a verified email (the same checks a normal OIDC login performs). Second, ask
     * {@link InviteService#acceptOidcInvite} to create the member; it also rejects the
     * acceptance when the IdP-verified email is not the email that was invited. Third, send the
     * browser onward: an organization member gets the browser session, same as logging in, and
     * lands in the web app; an application member is sent to the accept page's confirmation
     * state and signs in at the application's own UI.
     */
    @Override
    public void complete(RoutingContext ctx, CallbackResult<OidcConfiguration> result) {
        String token = result.inviteToken();
        Map<String, Object> claims = result.claims();

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

        inviteService.acceptOidcInvite(token, sub, result.config().getId(), email)
              .onSuccess(member -> {
                  if (member instanceof ApplicationParticipant user) {
                      // the management server holds organization logins only, so an application member
                      // signs in at the application
                      authEndpointSupport.redirectToUi(ctx, INVITE_ACCEPT_PATH + "?accepted=app&application="
                              + URLEncoder.encode(user.getApplicationId(), StandardCharsets.UTF_8));
                  } else {
                      authEndpointSupport.redirectSuccess(ctx, result.origin(), member);
                  }
              })
              .onFailure(err -> {
                  if (err instanceof InviteEmailMismatchException) {
                      // The invite is NOT consumed on mismatch — keep the token so the
                      // invitee can retry with another provider or a password.
                      redirectInviteError(ctx, OidcErrorCodes.EMAIL_MISMATCH, token);
                  } else if (err instanceof ExistingAccountException) {
                      // nor here: the account's holder accepts with the account's password
                      redirectInviteError(ctx, OidcErrorCodes.ACCOUNT_EXISTS, token);
                  } else if (err instanceof AccountDisabledException) {
                      redirectInviteError(ctx, OidcErrorCodes.ACCOUNT_DISABLED, token);
                  } else {
                      log.warn("Invite acceptance failed: {}", err.getMessage());
                      redirectInviteError(ctx, OidcErrorCodes.INVITE_INVALID, null);
                  }
              });
    }

    /**
     * Builds the IdP authorization redirect for the provider the invitee chose. The chosen
     * configId must be one the organization's login offers, which is the same list
     * {@link #handleDetails} rendered; anything else, and any application invitation, fails with
     * config_not_found. The flow carries the accept token on the session and returns to the
     * configuration's callback.
     */
    private Future<String> startFlowForInvite(RoutingContext ctx, PendingInvite invite, String configId, String token) {
        if (invite.getApplicationId() != null) {
            return Future.failedFuture(new OidcCallbackException(OidcErrorCodes.CONFIG_NOT_FOUND));
        }
        return oidcConfigurationService.findEnabledForScope(invite.getOrganizationId(), null)
                     .compose(offered -> {
                         OidcConfiguration chosen = offered.stream()
                                                              .filter(c -> configId.equals(c.getId()))
                                                              .findFirst()
                                                              .orElse(null);
                         Future<String> ret;
                         if (chosen == null) {
                             ret = Future.failedFuture(new OidcCallbackException(OidcErrorCodes.CONFIG_NOT_FOUND));
                         } else {
                             ret = oidcFlowOrchestrator.startInvitation(ctx, chosen,
                                                                        OrganizationOidcCallbacks.callbackUrl(properties.getApiBaseUrl(), chosen),
                                                                        invite.getOrganizationId(), token);
                         }
                         return ret;
                     });
    }

    /**
     * {@code 302 Location: <this server's UI>/invite/accept?error=<code>}. A non-null token is
     * kept in the URL so the page can reload the invitation and let the invitee try
     * another method.
     */
    private void redirectInviteError(RoutingContext ctx, String errorCode, String token) {
        String path = INVITE_ACCEPT_PATH + "?error=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8);
        if (token != null) {
            path += "&token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        }
        authEndpointSupport.redirectToUi(ctx, path);
    }

    /**
     * Invalid/expired tokens raise IllegalArgumentException with invitee-friendly messages;
     * surface those as 400s and keep everything else generic.
     */
    private void respondInviteFailure(RoutingContext ctx, Throwable err, String genericMessage) {
        if (err instanceof IllegalArgumentException) {
            authEndpointSupport.respondError(ctx, 400, err.getMessage());
        } else {
            log.warn("{}: {}", genericMessage, err.getMessage());
            authEndpointSupport.respondError(ctx, 500, genericMessage);
        }
    }
}
