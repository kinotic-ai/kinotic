package org.kinotic.gateway.internal.endpoints.rest.support;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.iam.BaseOidcConfiguration;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.PendingInvite;
import org.kinotic.domain.api.services.iam.InviteEmailMismatchException;
import org.kinotic.domain.api.services.iam.InviteService;
import org.kinotic.gateway.internal.endpoints.rest.OidcConstants;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The "IdP returned for an invitation accept" completion shared by the login callbacks.
 * A login callback whose {@link CallbackResult#inviteToken()} is non-null delegates here
 * instead of running its normal login lookup: the OIDC identity is validated the same way
 * as login, the member is created from the stored invite, and the browser is redirected.
 * All failures land back on the accept page with a typed {@code ?error=} code.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InviteAcceptSupport {

    private final InviteService inviteService;
    private final AuthEndpointSupport authEndpointSupport;

    /**
     * Completes acceptance of an ORGANIZATION-member invite riding the org login callbacks
     * (social or SSO). On success the member gets a console session and lands in the SPA.
     */
    public void completeOrgInviteAccept(RoutingContext ctx, CallbackResult<? extends BaseOidcConfiguration> result) {
        complete(ctx, result,
                 invite -> invite.getApplicationId() == null,
                 user -> authEndpointSupport.redirectSuccess(ctx, user));
    }

    /**
     * Completes acceptance of an APPLICATION-member invite riding that application's login
     * callback. On success the browser is sent to the accept page's confirmation state —
     * deliberately without a console session, since an app-scope user is not a console user.
     */
    public void completeAppInviteAccept(RoutingContext ctx,
                                        CallbackResult<? extends BaseOidcConfiguration> result,
                                        String orgId,
                                        String appId) {
        complete(ctx, result,
                 invite -> appId.equals(invite.getApplicationId()) && orgId.equals(invite.getOrganizationId()),
                 user -> ctx.response().setStatusCode(302)
                            .putHeader("Location", authEndpointSupport.appUrl(
                                    OidcConstants.INVITE_ACCEPT_PATH + "?accepted=app&application="
                                            + URLEncoder.encode(appId, StandardCharsets.UTF_8)))
                            .end());
    }

    /** {@code 302 Location: <appBaseUrl>/invite/accept?error=<code>}. */
    public void redirectInviteError(RoutingContext ctx, String errorCode) {
        redirectInviteError(ctx, errorCode, null);
    }

    /**
     * Error redirect that keeps the accept token in the URL, so the page can reload the
     * invitation and let the invitee try another method.
     */
    public void redirectInviteError(RoutingContext ctx, String errorCode, String token) {
        String location = authEndpointSupport.appUrl(OidcConstants.INVITE_ACCEPT_PATH)
                + "?error=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8);
        if (token != null) {
            location += "&token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        }
        ctx.response().setStatusCode(302).putHeader("Location", location).end();
    }

    /**
     * Shared completion: validates the OIDC identity (sub present, email verified — same
     * prologue as login), confirms the invite fits the callback route's scope, accepts via
     * the domain service (which enforces the invited-email match), then runs the
     * scope-specific success continuation.
     */
    private void complete(RoutingContext ctx,
                          CallbackResult<? extends BaseOidcConfiguration> result,
                          Predicate<PendingInvite> scopeCheck,
                          Consumer<IamUser> onAccepted) {
        String token = result.inviteToken();
        Map<String, Object> claims = result.claims();

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

        Future.fromCompletionStage(inviteService.getValidInvite(token))
              .compose(invite -> {
                  if (!scopeCheck.test(invite)) {
                      // e.g. an app invite riding an org callback — token and route disagree.
                      log.warn("Invite {} does not fit callback route scope", invite.getId());
                      return Future.failedFuture(new OidcCallbackException(OidcConstants.ERR_INVITE_INVALID));
                  }
                  return Future.fromCompletionStage(
                          inviteService.acceptOidcInvite(token, sub, result.config().getId(), email));
              })
              .onSuccess(onAccepted::accept)
              .onFailure(err -> {
                  Throwable cause = err.getCause() != null ? err.getCause() : err;
                  if (cause instanceof InviteEmailMismatchException) {
                      // The invite is NOT consumed on mismatch — keep the token so the
                      // invitee can retry with another provider or a password.
                      redirectInviteError(ctx, OidcConstants.ERR_EMAIL_MISMATCH, token);
                  } else if (cause instanceof OidcCallbackException oce) {
                      redirectInviteError(ctx, oce.getErrorCode(), token);
                  } else {
                      log.warn("Invite acceptance failed: {}", cause.getMessage());
                      redirectInviteError(ctx, OidcConstants.ERR_INVITE_INVALID);
                  }
              });
    }
}
