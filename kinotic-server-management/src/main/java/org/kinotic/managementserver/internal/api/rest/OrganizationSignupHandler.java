package org.kinotic.managementserver.internal.api.rest;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.management.api.config.KinoticManagementApiProperties;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.rest.support.*;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.exceptions.ExistingUserException;
import org.kinotic.domain.api.exceptions.PendingMemberException;
import org.kinotic.domain.api.model.security.PendingSignUp;
import org.kinotic.domain.api.rest.support.OidcCallbacks;
import org.kinotic.domain.api.services.security.SignUpService;
import org.kinotic.core.api.service.SuppliesGatewayRoutes;
import org.kinotic.managementserver.api.config.ManagementServerProperties;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Organization sign-up routes — the sign-up counterpart to {@link OrganizationLoginHandler}.
 * Both email/password and social (OIDC) sign-up create a new {@link Organization}, its founder's organization user
 * ({@link OrganizationParticipantIdentity}) and the founder's membership; each handler method documents its own step.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationSignupHandler implements SuppliesGatewayRoutes, OidcFlowCompleter {

    private final SignUpService signUpService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final AuthEndpointSupport authEndpointSupport;
    private final KinoticManagementApiProperties managementApiProperties;
    private final ManagementServerProperties managementServerProperties;

    @Override
    public void mountRoutes(Router router) {
        router.get("/api/auth/org/signup/methods").handler(this::handleMethods);
        // Email/password: form submit, then completion after the user clicks the email link.
        router.post("/api/auth/org/signup").handler(this::handleLocalSignUp);
        router.post("/api/auth/org/signup/complete").handler(this::handleLocalComplete);
        // Social (OIDC): start → IdP → the registration's callback → org-naming form → complete.
        router.post("/api/auth/org/signup/social/start/:provider").handler(this::handleSocialStart);
        router.post("/api/auth/org/signup/social/complete").handler(this::handleSocialCompleteOrg);
    }

    /**
     * {@code GET /api/auth/org/signup/methods} — the ways the portal's sign-up page offers: {@code password}, whether
     * it offers signing up with an email and a password beside GitHub.
     */
    private void handleMethods(RoutingContext ctx) {
        ctx.response()
           .putHeader("Content-Type", "application/json")
           .end(new JsonObject().put("password", managementServerProperties.isPasswordSignUp()).encode());
    }

    /**
     * {@code POST /api/auth/org/signup} — start email/password sign-up. Validates the request, stores a
     * pending sign-up, and emails a verification link with the default verification email template.
     * The org name and password are collected later, at {@link #handleLocalComplete}. When email is disabled
     * ({@code kinotic.domain.email.enabled} is {@code false}) the response carries the verification link as {@code verificationUrl}, for the UI to open at once. Refused with 403 unless
     * {@code kinotic.managementServer.passwordSignUp} is set.
     */
    private void handleLocalSignUp(RoutingContext ctx) {
        if (!managementServerProperties.isPasswordSignUp()) {
            ctx.response()
               .setStatusCode(403)
               .putHeader("Content-Type", "application/json")
               .end(new JsonObject().put("error", "Signing up with an email and a password is not offered.").encode());
            return;
        }
        try {
            JsonObject body = ctx.body().asJsonObject();
            String email = body.getString("email");
            String displayName = body.getString("displayName");

            signUpService.initiateLocalSignUp(email, displayName, null)
                    .onSuccess(verificationUrl -> ctx.response()
                            .setStatusCode(200)
                            .putHeader("Content-Type", "application/json")
                            .end((verificationUrl == null
                                    ? new JsonObject().put("message", "Verification email sent. Please check your inbox.")
                                    : new JsonObject().put("message", "Continue at the verification link.")
                                                      .put("verificationUrl", verificationUrl)).encode()))
                    .onFailure(ex -> {
                        log.warn("Sign-up failed: {}", ex.getMessage());
                        ctx.response()
                           .setStatusCode(400)
                           .putHeader("Content-Type", "application/json")
                           .end(new JsonObject().put("error", ex.getMessage()).encode());
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
                    .onSuccess(user -> authEndpointSupport.respondSuccess(ctx, user))
                    .onFailure(ex -> {
                        log.warn("Sign-up completion failed: {}", ex.getMessage());
                        ctx.response()
                           .setStatusCode(400)
                           .putHeader("Content-Type", "application/json")
                           .end(new JsonObject().put("error", ex.getMessage()).encode());
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
     * browser to the chosen Kinotic-curated social IdP, which returns to the registration's callback;
     * {@code 400} for an unknown or disabled provider.
     */
    private void handleSocialStart(RoutingContext ctx) {
        authEndpointSupport.handleSocialStart(ctx, config -> oidcFlowOrchestrator.startSignUp(
                ctx, config, OidcCallbacks.socialCallbackUrl(managementApiProperties.getManagementApi().getApiBaseUrl(), config.getId())));
    }

    @Override
    public OidcFlowPurpose purpose() {
        return OidcFlowPurpose.SIGN_UP;
    }

    @Override
    public void fail(RoutingContext ctx, PendingFlow flow, Throwable failure) {
        authEndpointSupport.redirectCallbackFailure(ctx, flow.origin(), failure);
    }

    /**
     * Turns a verified social identity into a pending sign-up: refuses if an {@link OrganizationParticipantIdentity}
     * already exists for this identity or its verified email, with {@code user_exists} when someone signs in as it
     * and {@code user_pending} when it is a member an administrator added that nobody signs in as yet, otherwise
     * stores a {@link PendingSignUp} carrying the verified identity and redirects the browser to the
     * org-naming page of the UI the sign-up returns to, which posts back to {@link #handleSocialCompleteOrg}.
     */
    @Override
    public void complete(RoutingContext ctx, CallbackResult<OidcConfiguration> result) {
        OidcConfiguration config = result.config();
        Map<String, Object> claims = result.claims();

        String sub = OAuth2Util.stringClaim(claims, "sub");
        String email = OAuth2Util.stringClaim(claims, "email");
        String displayName = OAuth2Util.firstPresent(claims, "name", "preferred_username", "email");

        if (sub == null || email == null) {
            authEndpointSupport.redirectError(ctx, result.origin(), OidcErrorCodes.INVALID_TOKEN);
            return;
        }
        if (!OAuth2Util.isEmailVerified(claims, config.getProvider())) {
            authEndpointSupport.redirectError(ctx, result.origin(), OidcErrorCodes.EMAIL_NOT_VERIFIED);
            return;
        }

        PendingSignUp pending = new PendingSignUp();
        pending.setOidcSubject(sub);
        pending.setOidcConfigId(config.getId());
        pending.setEmail(email);
        pending.setDisplayName(displayName);
        signUpService.createOidcPending(pending)
              .onSuccess(created -> redirectToCompleteOrg(ctx, result.origin(), created.getVerificationToken()))
              .onFailure(ex -> {
                  if (ex instanceof PendingMemberException) {
                      authEndpointSupport.redirectError(ctx, result.origin(), OidcErrorCodes.USER_PENDING);
                  } else if (ex instanceof ExistingUserException) {
                      authEndpointSupport.redirectError(ctx, result.origin(), OidcErrorCodes.USER_EXISTS);
                  } else {
                      log.warn("Signup resolution failed: {}", ex.getMessage());
                      authEndpointSupport.redirectError(ctx, result.origin(), OidcErrorCodes.SIGNUP_FAILED);
                  }
              });
    }

    /**
     * {@code POST /api/auth/org/signup/social/complete} — finish social sign-up. The user has named their org;
     * creates the organization, the founder's organization user bound to the verified identity, and the membership, from
     * the pending sign-up identified by the token.
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

        signUpService.completeOidcWithNewOrg(token, orgName, orgDescription)
              .onSuccess(user -> authEndpointSupport.respondSuccess(ctx, user))
              .onFailure(ex -> authEndpointSupport.respondError(ctx, 400, ex.getMessage()));
    }

    /**
     * Sends the browser to the org-naming page of the UI at {@code origin} with the pending sign-up token, or of this
     * server's UI when {@code origin} is null.
     */
    private void redirectToCompleteOrg(RoutingContext ctx, String origin, String token) {
        authEndpointSupport.redirectToPage(ctx, origin, "/register?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8));
    }

}
