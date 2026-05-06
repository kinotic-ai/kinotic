package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.security.AuthScopeType;
import org.kinotic.gateway.internal.auth.CallbackResult;
import org.kinotic.gateway.internal.auth.AuthEndpointSupport;
import org.kinotic.gateway.internal.auth.OAuth2AuthFactory;
import org.kinotic.gateway.internal.auth.OidcFlowOrchestrator;
import org.kinotic.gateway.internal.auth.SessionKeys;
import org.kinotic.os.api.model.iam.IamUser;
import org.kinotic.os.api.model.iam.OidcProviderKind;
import org.kinotic.os.api.model.iam.OrgSignupOidcConfiguration;
import org.kinotic.os.api.model.iam.PendingRegistration;
import org.kinotic.os.api.services.iam.IamUserService;
import org.kinotic.os.api.services.iam.OrgSignupOidcConfigurationService;
import org.kinotic.os.api.services.iam.PendingRegistrationService;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Signup-with-social routes — distinct from {@link OrganizationLoginHandler}'s login routes.
 * <ul>
 *   <li>{@code POST /api/signup/start/:provider} — initiates the IdP flow with signup
 *       intent. The user has no org yet; we redirect to the platform-wide social provider
 *       (Google, etc.) and stash the configId on the session.</li>
 *   <li>{@code GET /api/signup/callback/:configId} — handles the IdP response. Validates
 *       state, exchanges the code, checks {@code email_verified}, refuses if an
 *       {@link IamUser} already exists for {@code (sub, configId)}, otherwise stashes the
 *       verified identity in a {@link PendingRegistration} and redirects the browser to
 *       the org-name completion page.</li>
 *   <li>{@code POST /api/signup/complete-org} — consumes the pending registration token,
 *       creates the {@link org.kinotic.os.api.model.Organization} with the supplied name,
 *       creates the admin {@link IamUser} (AuthType=OIDC), and returns a Kinotic JWT for
 *       the STOMP CONNECT.</li>
 * </ul>
 * The basic email/password signup path stays in {@link SignUpHandler}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OidcSignupHandler {

    private static final SessionKeys SESSION_KEYS = SessionKeys.ofPrefix("signup");

    private final IamUserService iamUserService;
    private final OrgSignupOidcConfigurationService orgSignupOidcConfigurationService;
    private final PendingRegistrationService pendingRegistrationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final AuthEndpointSupport authEndpointSupport;

    public void mountRoutes(Router router) {
        authEndpointSupport.installSessionHandler(router, OidcConstants.SIGNUP_BASE);

        router.post(OidcConstants.SIGNUP_BASE + "/start/:provider").handler(this::handleStart);
        router.get(OidcConstants.SIGNUP_BASE + "/callback/:configId").handler(this::handleCallback);
        router.post(OidcConstants.SIGNUP_BASE + "/complete-org").handler(this::handleCompleteOrg);
    }

    private void handleStart(RoutingContext ctx) {
        String provider = ctx.pathParam("provider");
        OidcProviderKind providerKind;
        try {
            providerKind = OidcProviderKind.fromKey(provider);
        } catch (IllegalArgumentException ex) {
            authEndpointSupport.respondError(ctx, 400, "Unknown platform provider: " + provider);
            return;
        }

        Future.fromCompletionStage(orgSignupOidcConfigurationService.findEnabledByProvider(providerKind))
              .compose(config -> {
                  if (config == null) {
                      authEndpointSupport.respondError(ctx, 400, "Unknown or disabled platform provider: " + provider);
                      return Future.<String>succeededFuture();
                  }
                  return oidcFlowOrchestrator.startFlow(ctx, config, SESSION_KEYS,
                                                       callbackUrl(config.getId()), null);
              })
              .onSuccess(url -> {
                  if (url != null) {
                      ctx.response().setStatusCode(302).putHeader("Location", url).end();
                  }
              })
              .onFailure(ex -> {
                  log.error("Signup start failed for provider {}", provider, ex);
                  authEndpointSupport.respondError(ctx, 500, "Provider initialization failed");
              });
    }

    private void handleCallback(RoutingContext ctx) {
        String pathConfigId = ctx.pathParam("configId");

        oidcFlowOrchestrator.<OrgSignupOidcConfiguration>handleCallback(
                ctx, pathConfigId, SESSION_KEYS, callbackUrl(pathConfigId),
                orgSignupOidcConfigurationService::findById)
                .onSuccess(result -> resolveSignup(ctx, result))
                .onFailure(ex -> authEndpointSupport.redirectCallbackFailure(ctx, ex));
    }

    /**
     * After IdP returns: refuse if {@code (sub, configId)} already maps to an existing
     * IamUser anywhere (the user already has an account — they should log in, not sign up).
     * Otherwise create a {@link PendingRegistration} carrying the verified identity and
     * redirect to the org-name completion page.
     */
    private void resolveSignup(RoutingContext ctx, CallbackResult<OrgSignupOidcConfiguration> result) {
        OrgSignupOidcConfiguration config = result.config();
        Map<String, Object> claims = result.claims();

        String sub = OidcFlowOrchestrator.stringClaim(claims, "sub");
        String email = OidcFlowOrchestrator.stringClaim(claims, "email");
        String displayName = OidcFlowOrchestrator.firstPresent(claims, "name", "preferred_username", "email");

        if (sub == null || email == null) {
            authEndpointSupport.redirectError(ctx, OidcConstants.ERR_INVALID_TOKEN);
            return;
        }
        if (!OAuth2AuthFactory.isEmailVerified(claims, config.getProvider())) {
            authEndpointSupport.redirectError(ctx, OidcConstants.ERR_EMAIL_NOT_VERIFIED);
            return;
        }

        Future.fromCompletionStage(iamUserService.findByOidcIdentity(sub, config.getId()))
              .compose(existing -> {
                  if (existing != null && !existing.isEmpty()) {
                      // Already have an account for this identity — push them to log in instead.
                      return Future.<PendingRegistration>failedFuture(new AccountExistsException());
                  }
                  PendingRegistration pending = new PendingRegistration()
                          .setOidcSubject(sub)
                          .setOidcConfigId(config.getId())
                          .setEmail(email)
                          .setDisplayName(displayName)
                          .setAuthScopeType(AuthScopeType.ORGANIZATION.name())  // placeholder — actual orgId set on complete
                          .setAuthScopeId("__pending__")
                          .setAdditionalClaims(claims);
                  return Future.fromCompletionStage(pendingRegistrationService.create(pending));
              })
              .onSuccess(pending -> redirectToCompleteOrg(ctx, pending.getVerificationToken()))
              .onFailure(ex -> {
                  Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                  if (cause instanceof AccountExistsException) {
                      authEndpointSupport.redirectError(ctx, OidcConstants.ERR_ACCOUNT_EXISTS);
                  } else {
                      log.warn("Signup resolution failed: {}", cause.getMessage());
                      authEndpointSupport.redirectError(ctx, OidcConstants.ERR_SIGNUP_FAILED);
                  }
              });
    }

    private void handleCompleteOrg(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String token = body == null ? null : body.getString("token");
        String orgName = body == null ? null : body.getString("orgName");
        String orgDescription = body == null ? null : body.getString("orgDescription");

        if (token == null || token.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "token is required");
            return;
        }
        if (orgName == null || orgName.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "orgName is required");
            return;
        }

        Future.fromCompletionStage(pendingRegistrationService.completeWithNewOrg(token, orgName, orgDescription))
              .onSuccess(user -> authEndpointSupport.respondJwt(ctx, user,
                      new JsonObject().put("orgId", user.getAuthScopeId())))
              .onFailure(ex -> {
                  Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                  authEndpointSupport.respondError(ctx, 400, cause.getMessage());
              });
    }

    private String callbackUrl(String configId) {
        return authEndpointSupport.absoluteUrl(OidcConstants.SIGNUP_BASE + "/callback/" + configId);
    }

    /** Sends the browser to the org-name completion page with the pending registration token. */
    private void redirectToCompleteOrg(RoutingContext ctx, String token) {
        ctx.response().setStatusCode(302)
           .putHeader("Location", OidcConstants.REGISTER_PATH + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8))
           .end();
    }

    private static class AccountExistsException extends RuntimeException {}
}
