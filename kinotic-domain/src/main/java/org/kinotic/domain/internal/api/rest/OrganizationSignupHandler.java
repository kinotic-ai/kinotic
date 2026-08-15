package org.kinotic.domain.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.internal.api.rest.support.*;
import org.kinotic.domain.api.model.security.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.OrgSignupOidcConfiguration;
import org.kinotic.domain.api.model.security.PendingSignUp;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.OrgSignupOidcConfigurationService;
import org.kinotic.domain.api.services.security.SignUpService;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Organization sign-up routes — the sign-up counterpart to {@link OrganizationLoginHandler}.
 * Both email/password and social (OIDC) sign-up create a new {@link Organization} and its admin
 * {@link UserParticipantIdentity}; each handler method documents its own step.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationSignupHandler implements SuppliesGatewayRoutes {

    private final ParticipantIdentityService identityService;
    private final OrgSignupOidcConfigurationService orgSignupOidcConfigurationService;
    private final SignUpService signUpService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final AuthEndpointSupport authEndpointSupport;

    @Override
    public void mountRoutes(Router router) {
        // Email/password: form submit, then completion after the user clicks the email link.
        router.post("/api/auth/org/signup").handler(this::handleLocalSignUp);
        router.post("/api/auth/org/signup/complete").handler(this::handleLocalComplete);
        // Social (OIDC): start → IdP → callback → org-naming form → complete.
        router.post("/api/auth/org/signup/social/start/:provider").handler(this::handleSocialStart);
        router.get("/api/auth/org/signup/social/callback/:configId").handler(this::handleSocialCallback);
        router.post("/api/auth/org/signup/social/complete").handler(this::handleSocialCompleteOrg);
    }

    /**
     * {@code POST /api/auth/org/signup} — start email/password sign-up. Validates the request, stores a
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
     * {@code POST /api/auth/org/signup/complete} — finish email/password sign-up. Called when the user has
     * clicked the verification link and submitted an org name + password; creates the organization,
     * its admin user, and the password credential, then establishes the browser session so the
     * user is logged in — same as the social completion at {@link #handleSocialCompleteOrg}.
     */
    private void handleLocalComplete(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            String token = body.getString("token");
            String orgName = body.getString("orgName");
            String orgDescription = body.getString("orgDescription");
            String password = body.getString("password");

            signUpService.completeLocalSignUp(token, orgName, orgDescription, password)
                    .thenAccept(user -> authEndpointSupport.respondSuccess(ctx, user))
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
     * {@code POST /api/auth/org/signup/social/start/:provider} — start social (OIDC) sign-up by redirecting the
     * browser to the chosen Kinotic-curated social IdP.
     */
    private void handleSocialStart(RoutingContext ctx) {
        authEndpointSupport.handleSocialStart(ctx, this::callbackUrl);
    }

    /**
     * {@code GET /api/auth/org/signup/social/callback/:configId} — the social IdP returns here. Validates the
     * callback (state, code exchange, issuer) via {@link OidcFlowOrchestrator}, then hands the
     * verified claims to {@link #createPendingSignUp}.
     */
    private void handleSocialCallback(RoutingContext ctx) {
        String pathConfigId = ctx.pathParam("configId");

        oidcFlowOrchestrator.handleCallback(
                ctx, pathConfigId, callbackUrl(pathConfigId),
                _ -> orgSignupOidcConfigurationService.findById(pathConfigId))
                .onSuccess(result -> createPendingSignUp(ctx, result))
                .onFailure(ex -> authEndpointSupport.redirectCallbackFailure(ctx, ex));
    }

    /**
     * Turns a verified social identity into a pending sign-up: refuses if an {@link UserParticipantIdentity}
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
            authEndpointSupport.redirectError(ctx, OidcErrorCodes.INVALID_TOKEN);
            return;
        }
        if (!OAuth2Util.isEmailVerified(claims, config.getProvider())) {
            authEndpointSupport.redirectError(ctx, OidcErrorCodes.EMAIL_NOT_VERIFIED);
            return;
        }

        identityService.findOrgUserByOidcIdentity(sub, config.getId())
              .compose(existing -> {
                  if (existing != null) {
                      // Already have an account for this identity — push them to log in instead.
                      return Future.failedFuture(new AccountExistsException());
                  }
                  PendingSignUp pending = new PendingSignUp();
                  pending.setOidcSubject(sub);
                  pending.setOidcConfigId(config.getId());
                  pending.setEmail(email);
                  pending.setDisplayName(displayName);
                  return Future.fromCompletionStage(signUpService.createOidcPending(pending));
              })
              .onSuccess(pending -> redirectToCompleteOrg(ctx, pending.getVerificationToken()))
              .onFailure(ex -> {
                  Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                  if (cause instanceof AccountExistsException) {
                      authEndpointSupport.redirectError(ctx, OidcErrorCodes.ACCOUNT_EXISTS);
                  } else {
                      log.warn("Signup resolution failed: {}", cause.getMessage());
                      authEndpointSupport.redirectError(ctx, OidcErrorCodes.SIGNUP_FAILED);
                  }
              });
    }

    /**
     * {@code POST /api/auth/org/signup/social/complete} — finish social sign-up. The user has named their org;
     * creates the organization and its admin {@link UserParticipantIdentity} (AuthType=OIDC) from the pending
     * sign-up identified by the token.
     */
    private void handleSocialCompleteOrg(RoutingContext ctx) {
        JsonObject body = authEndpointSupport.readJsonBody(ctx);
        String token = body.getString("token");
        String orgName = body.getString("orgName");
        String orgDescription = body.getString("orgDescription");

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
        return authEndpointSupport.absoluteUrl("/api/auth/org/signup/social/callback/" + configId);
    }

    /** Sends the browser to the org-naming page with the pending sign-up token. */
    private void redirectToCompleteOrg(RoutingContext ctx, String token) {
        ctx.response().setStatusCode(302)
           .putHeader("Location", authEndpointSupport.appUrl("/register")
                   + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8))
           .end();
    }

    private static class AccountExistsException extends RuntimeException {}
}
