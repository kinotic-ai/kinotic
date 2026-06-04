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
 * which likewise serves both password and OIDC for org login. Both paths here create a new
 * {@link Organization} and its admin {@link IamUser}.
 * <ul>
 *   <li>Email/password: {@code POST /api/signup} stores a pending sign-up and emails a
 *       verification link; {@code POST /api/signup/complete} then creates the org, admin, and
 *       password credential once the user clicks the link and names the org.</li>
 *   <li>Social (OIDC): {@code POST /api/signup/start/:provider} initiates the IdP flow;
 *       {@code GET /api/signup/callback/:configId} validates the response, refuses if an
 *       {@link IamUser} already exists for {@code (sub, configId)}, otherwise stashes the verified
 *       identity in a {@link PendingSignUp} and redirects to the org-name completion page;
 *       {@code POST /api/signup/complete-org} then creates the org + admin (AuthType=OIDC).</li>
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
        // Email/password sign-up
        router.post("/api/signup").handler(this::handleSignUp);
        router.post("/api/signup/complete").handler(this::handleSignUpComplete);
        // Social (OIDC) sign-up
        router.post(OidcConstants.SIGNUP_BASE + "/start/:provider").handler(this::handleStart);
        router.get(OidcConstants.SIGNUP_BASE + "/callback/:configId").handler(this::handleCallback);
        router.post(OidcConstants.SIGNUP_BASE + "/complete-org").handler(this::handleCompleteOrg);
    }

    private void handleSignUp(RoutingContext ctx) {
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

    private void handleSignUpComplete(RoutingContext ctx) {
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

    private void handleCallback(RoutingContext ctx) {
        String pathConfigId = ctx.pathParam("configId");

        oidcFlowOrchestrator.<OrgSignupOidcConfiguration>handleCallback(
                ctx, pathConfigId, callbackUrl(pathConfigId),
                _ -> orgSignupOidcConfigurationService.findById(pathConfigId))
                .onSuccess(result -> resolveSignup(ctx, result))
                .onFailure(ex -> authEndpointSupport.redirectCallbackFailure(ctx, ex));
    }

    /**
     * After IdP returns: refuse if {@code (sub, configId)} already maps to an existing
     * IamUser anywhere (the user already has an account — they should log in, not sign up).
     * Otherwise, create a {@link PendingSignUp} carrying the verified identity and
     * redirect to the org-name completion page.
     */
    private void resolveSignup(RoutingContext ctx, CallbackResult<OrgSignupOidcConfiguration> result) {
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

    /** Sends the browser to the org-name completion page with the pending registration token. */
    private void redirectToCompleteOrg(RoutingContext ctx, String token) {
        ctx.response().setStatusCode(302)
           .putHeader("Location", authEndpointSupport.appUrl(OidcConstants.REGISTER_PATH)
                   + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8))
           .end();
    }

    private static class AccountExistsException extends RuntimeException {}
}
