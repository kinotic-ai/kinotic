package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.gateway.internal.endpoints.rest.support.*;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.OidcProviderKind;
import org.kinotic.domain.api.model.iam.OrgSignupOidcConfiguration;
import org.kinotic.domain.api.model.iam.PendingSignUp;
import org.kinotic.domain.api.services.iam.IamUserService;
import org.kinotic.domain.api.services.iam.OrgSignupOidcConfigurationService;
import org.kinotic.domain.api.services.iam.SignUpService;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * All organization sign-up routes — the sign-up counterpart to {@link OrganizationLoginHandler},
 * which likewise serves both password and OIDC for org login. Both flows here create a new
 * {@link Organization} and its admin {@link IamUser}, each in two steps:
 * <ul>
 *   <li><b>Email/password</b>: {@link #handleLocalSignUp} (store pending + email a link) →
 *       {@link #handleLocalComplete} (create org + admin + credential).</li>
 *   <li><b>Social (OIDC)</b>: {@link #handleSocialStart} → IdP → {@link #handleSocialCallback}
 *       (which stashes the verified identity via {@link #createPendingSignUp}) →
 *       {@link #handleSocialCompleteOrg} (create org + admin).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationSignupHandler {

    private final IamUserService iamUserService;
    private final OrgSignupOidcConfigurationService orgSignupOidcConfigurationService;
    private final SignUpService signUpService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final AuthEndpointSupport authEndpointSupport;

    public void mountRoutes(Router router) {
        // Email/password: form submit, then completion after the user clicks the email link.
        router.post("/api/signup").handler(this::handleLocalSignUp);
        router.post("/api/signup/complete").handler(this::handleLocalComplete);
        // Social (OIDC): start → IdP → callback → org-naming form → complete.
        router.post(OidcConstants.SIGNUP_BASE + "/start/:provider").handler(this::handleSocialStart);
        router.get(OidcConstants.SIGNUP_BASE + "/callback/:configId").handler(this::handleSocialCallback);
        router.post(OidcConstants.SIGNUP_BASE + "/complete-org").handler(this::handleSocialCompleteOrg);
    }

    /**
     * {@code POST /api/signup} — start email/password sign-up. Validates the request, stores a
     * pending sign-up, and emails a verification link. The org name and password are collected
     * later, at {@link #handleLocalComplete}.
     */
    private void handleLocalSignUp(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            String email = body.getString("email");
            String displayName = body.getString("displayName");

            signUpService.initiateLocalSignUp(email, displayName)
                    .thenAccept(v -> ctx.response()
                            .setStatusCode(200)
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("message", "Verification email sent. Please check your inbox.").encode()))
                    .exceptionally(ex -> {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        log.warn("Sign-up failed: {}", cause.getMessage());
                        ctx.response()
                           .setStatusCode(400)
                           .putHeader("Content-Type", "application/json")
                           .end(new JsonObject().put("error", cause.getMessage()).encode());
                        return null;
                    });
        } catch (Exception e) {
            log.error("Failed to parse sign-up request", e);
            ctx.response()
               .setStatusCode(400)
               .putHeader("Content-Type", "application/json")
               .end(new JsonObject().put("error", "Invalid request body").encode());
        }
    }

    /**
     * {@code POST /api/signup/complete} — finish email/password sign-up. Called when the user has
     * clicked the verification link and submitted an org name + password; creates the organization,
     * its admin user, and the password credential.
     */
    private void handleLocalComplete(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            String token = body.getString("token");
            String orgName = body.getString("orgName");
            String orgDescription = body.getString("orgDescription");
            String password = body.getString("password");

            signUpService.completeLocalSignUp(token, orgName, orgDescription, password)
                    .thenAccept(orgId -> ctx.response()
                            .setStatusCode(200)
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject()
                                    .put("message", "Account created successfully")
                                    .put("orgId", orgId)
                                    .encode()))
                    .exceptionally(ex -> {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        log.warn("Sign-up completion failed: {}", cause.getMessage());
                        ctx.response()
                           .setStatusCode(400)
                           .putHeader("Content-Type", "application/json")
                           .end(new JsonObject().put("error", cause.getMessage()).encode());
                        return null;
                    });
        } catch (Exception e) {
            log.error("Failed to parse sign-up completion request", e);
            ctx.response()
               .setStatusCode(400)
               .putHeader("Content-Type", "application/json")
               .end(new JsonObject().put("error", "Invalid request body").encode());
        }
    }

    /**
     * {@code POST /api/signup/start/:provider} — start social (OIDC) sign-up by redirecting the
     * browser to the chosen Kinotic-curated social IdP.
     */
    private void handleSocialStart(RoutingContext ctx) {
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
                      return Future.succeededFuture();
                  }
                  return oidcFlowOrchestrator.startFlow(ctx, config, callbackUrl(config.getId()), null);
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

    /**
     * {@code GET /api/signup/callback/:configId} — the social IdP returns here. Validates the
     * callback (state, code exchange, issuer) via {@link OidcFlowOrchestrator}, then hands the
     * verified claims to {@link #createPendingSignUp}.
     */
    private void handleSocialCallback(RoutingContext ctx) {
        String pathConfigId = ctx.pathParam("configId");

        oidcFlowOrchestrator.<OrgSignupOidcConfiguration>handleCallback(
                ctx, pathConfigId, callbackUrl(pathConfigId),
                _ -> orgSignupOidcConfigurationService.findById(pathConfigId))
                .onSuccess(result -> createPendingSignUp(ctx, result))
                .onFailure(ex -> authEndpointSupport.redirectCallbackFailure(ctx, ex));
    }

    /**
     * Turns a verified social identity into a pending sign-up: refuses if an {@link IamUser}
     * already exists for this identity (they should log in, not sign up), otherwise stores a
     * {@link PendingSignUp} carrying the verified identity and redirects the browser to the
     * org-naming page that posts back to {@link #handleSocialCompleteOrg}.
     */
    private void createPendingSignUp(RoutingContext ctx, CallbackResult<OrgSignupOidcConfiguration> result) {
        OrgSignupOidcConfiguration config = result.config();
        Map<String, Object> claims = result.claims();

        String sub = OAuth2Util.stringClaim(claims, "sub");
        String email = OAuth2Util.stringClaim(claims, "email");
        String displayName = OAuth2Util.firstPresent(claims, "name", "preferred_username", "email");

        if (sub == null || email == null) {
            authEndpointSupport.redirectError(ctx, OidcConstants.ERR_INVALID_TOKEN);
            return;
        }
        if (!OAuth2Util.isEmailVerified(claims, config.getProvider())) {
            authEndpointSupport.redirectError(ctx, OidcConstants.ERR_EMAIL_NOT_VERIFIED);
            return;
        }

        Future.fromCompletionStage(iamUserService.findAllByOidcIdentity(sub, config.getId()))
              .compose(existing -> {
                  if (existing != null && !existing.isEmpty()) {
                      // Already have an account for this identity — push them to log in instead.
                      return Future.<PendingSignUp>failedFuture(new AccountExistsException());
                  }
                  PendingSignUp pending = new PendingSignUp();
                  pending.setOidcSubject(sub);
                  pending.setOidcConfigId(config.getId());
                  pending.setEmail(email);
                  pending.setDisplayName(displayName);
                  pending.setAdditionalClaims(claims);
                  return Future.fromCompletionStage(signUpService.createOidcPending(pending));
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

    /**
     * {@code POST /api/signup/complete-org} — finish social sign-up. The user has named their org;
     * creates the organization and its admin {@link IamUser} (AuthType=OIDC) from the pending
     * sign-up identified by the token.
     */
    private void handleSocialCompleteOrg(RoutingContext ctx) {
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

        Future.fromCompletionStage(signUpService.completeOidcWithNewOrg(token, orgName, orgDescription))
              .onSuccess(user -> authEndpointSupport.respondSuccess(ctx, user))
              .onFailure(ex -> {
                  Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                  authEndpointSupport.respondError(ctx, 400, cause.getMessage());
              });
    }

    private String callbackUrl(String configId) {
        return authEndpointSupport.absoluteUrl(OidcConstants.SIGNUP_BASE + "/callback/" + configId);
    }

    /** Sends the browser to the org-naming page with the pending sign-up token. */
    private void redirectToCompleteOrg(RoutingContext ctx, String token) {
        ctx.response().setStatusCode(302)
           .putHeader("Location", authEndpointSupport.appUrl(OidcConstants.REGISTER_PATH)
                   + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8))
           .end();
    }

    private static class AccountExistsException extends RuntimeException {}
}
