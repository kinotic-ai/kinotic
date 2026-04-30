package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2AuthorizationURL;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.Oauth2Credentials;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.internal.security.KinoticJwtIssuer;
import org.kinotic.gateway.api.config.KinoticApiGatewayProperties;
import org.kinotic.gateway.internal.auth.OAuth2AuthFactory;
import org.kinotic.gateway.internal.auth.OAuth2AuthRegistry;
import org.kinotic.os.api.model.iam.IamUser;
import org.kinotic.os.api.model.iam.OidcConfiguration;
import org.kinotic.os.api.model.iam.OidcProviderKind;
import org.kinotic.os.api.model.iam.PendingRegistration;
import org.kinotic.os.api.services.KinoticSystemService;
import org.kinotic.os.api.services.iam.IamUserService;
import org.kinotic.os.api.services.iam.OidcConfigurationService;
import org.kinotic.os.api.services.iam.PendingRegistrationService;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Signup-with-social routes — distinct from {@link LoginHandler}'s login routes.
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

    private static final int JWT_TTL_SECONDS = 60;

    // Session keys (prefixed differently from the login handler so the two flows don't collide
    // in a shared cookie scope).
    private static final String S_STATE = "signup.state";
    private static final String S_NONCE = "signup.nonce";
    private static final String S_PKCE = "signup.pkce";
    private static final String S_CONFIG_ID = "signup.configId";

    private final Vertx vertx;
    private final KinoticApiGatewayProperties gatewayProperties;
    private final IamUserService iamUserService;
    private final OidcConfigurationService oidcConfigurationService;
    private final KinoticSystemService kinoticSystemService;
    private final OAuth2AuthRegistry oauth2AuthRegistry;
    private final PendingRegistrationService pendingRegistrationService;
    private final KinoticJwtIssuer jwtIssuer;

    public void mountRoutes(Router router) {
        router.route("/api/signup/*").handler(RedirectFlowSessionSupport.newSessionHandler(vertx));

        router.post("/api/signup/start/:provider").handler(this::handleStart);
        router.get("/api/signup/callback/:configId").handler(this::handleCallback);
        router.post("/api/signup/complete-org").handler(this::handleCompleteOrg);
    }

    // ── /api/signup/start/:provider ───────────────────────────────────────────

    private void handleStart(RoutingContext ctx) {
        String provider = ctx.pathParam("provider");
        OidcProviderKind providerKind;
        try {
            providerKind = OidcProviderKind.fromKey(provider);
        } catch (IllegalArgumentException ex) {
            respondError(ctx, 400, "Unknown platform provider: " + provider);
            return;
        }

        Future.fromCompletionStage(kinoticSystemService.getOidcConfigurations())
              .compose(configs -> {
                  OidcConfiguration match = null;
                  for (OidcConfiguration c : configs) {
                      if (providerKind == c.getProvider()) { match = c; break; }
                  }
                  if (match == null) {
                      respondError(ctx, 400, "Unknown or disabled platform provider: " + provider);
                      return Future.succeededFuture();
                  }
                  return prepareAuthorizeUrl(ctx, match)
                          .map(url -> {
                              ctx.response().setStatusCode(302).putHeader("Location", url).end();
                              return null;
                          });
              })
              .onFailure(ex -> {
                  log.error("Signup start failed for provider {}", provider, ex);
                  respondError(ctx, 500, "Provider initialization failed");
              });
    }

    private Future<String> prepareAuthorizeUrl(RoutingContext ctx, OidcConfiguration config) {
        String state = RedirectFlowSessionSupport.randomUrlSafe(32);
        String nonce = RedirectFlowSessionSupport.randomUrlSafe(32);
        String pkceVerifier = RedirectFlowSessionSupport.randomUrlSafe(64);
        String pkceChallenge = RedirectFlowSessionSupport.s256Challenge(pkceVerifier);

        Session session = ctx.session();
        session.regenerateId();
        session.put(S_STATE, state);
        session.put(S_NONCE, nonce);
        session.put(S_PKCE, pkceVerifier);
        session.put(S_CONFIG_ID, config.getId());

        return oauth2AuthRegistry.get(config, config.getId()).map(oauth2 -> oauth2.authorizeURL(
                new OAuth2AuthorizationURL()
                        .setRedirectUri(callbackUrl(config.getId()))
                        .setScopes(List.of("openid", "email", "profile"))
                        .setState(state)
                        .setCodeChallenge(pkceChallenge)
                        .setCodeChallengeMethod("S256")
                        .putAdditionalParameter("nonce", nonce)));
    }

    // ── /api/signup/callback/:configId ────────────────────────────────────────

    private void handleCallback(RoutingContext ctx) {
        String pathConfigId = ctx.pathParam("configId");
        String code = ctx.request().getParam("code");
        String state = ctx.request().getParam("state");
        String idpError = ctx.request().getParam("error");

        if (idpError != null) {
            log.info("Signup callback error from config {}: {}", pathConfigId, idpError);
            redirectToError(ctx, idpError);
            return;
        }
        if (code == null || state == null) {
            redirectToError(ctx, "invalid_callback");
            return;
        }

        Session session = ctx.session();
        String expectedState = session.get(S_STATE);
        String pkceVerifier = session.get(S_PKCE);
        String sessionConfigId = session.get(S_CONFIG_ID);
        session.destroy();

        if (expectedState == null || !expectedState.equals(state)
                || sessionConfigId == null || !sessionConfigId.equals(pathConfigId)) {
            log.warn("Signup callback state mismatch for configId={}", pathConfigId);
            redirectToError(ctx, "state_mismatch");
            return;
        }

        Future.fromCompletionStage(oidcConfigurationService.findById(pathConfigId))
              .compose(config -> {
                  if (config == null) {
                      return Future.failedFuture(new IllegalStateException("OidcConfiguration not found"));
                  }
                  return oauth2AuthRegistry.get(config, config.getId())
                                           .compose(oauth2 -> exchangeCode(oauth2, config, code, pkceVerifier))
                                           .map(user -> Map.entry(config, user));
              })
              .onSuccess(entry -> resolveSignup(ctx, entry.getKey(), entry.getValue()))
              .onFailure(ex -> {
                  log.warn("Signup callback failed: {}", ex.getMessage());
                  redirectToError(ctx, "exchange_failed");
              });
    }

    private Future<User> exchangeCode(OAuth2Auth oauth2, OidcConfiguration config, String code, String pkce) {
        return oauth2.authenticate(new Oauth2Credentials()
                .setFlow(OAuth2FlowType.AUTH_CODE)
                .setCode(code)
                .setRedirectUri(callbackUrl(config.getId()))
                .setCodeVerifier(pkce));
    }

    /**
     * After IdP returns: refuse if {@code (sub, configId)} already maps to an existing
     * IamUser anywhere (the user already has an account — they should log in, not sign up).
     * Otherwise create a {@link PendingRegistration} carrying the verified identity and
     * redirect to the org-name completion page.
     */
    private void resolveSignup(RoutingContext ctx, OidcConfiguration config, User idpUser) {
        Map<String, Object> claims = flattenClaims(idpUser);

        if (!OAuth2AuthFactory.isIssuerValid(claims, config.getAuthority())) {
            log.warn("OIDC issuer validation failed for config {}: iss={}, tid={}",
                     config.getId(), claims.get("iss"), claims.get("tid"));
            redirectToError(ctx, "invalid_token");
            return;
        }

        String sub = stringClaim(claims, "sub");
        String email = stringClaim(claims, "email");
        String displayName = firstPresent(claims, "name", "preferred_username", "email");

        if (sub == null || email == null) {
            redirectToError(ctx, "invalid_token");
            return;
        }
        if (!OAuth2AuthFactory.isEmailVerified(claims, config.getProvider())) {
            redirectToError(ctx, "email_not_verified");
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
                          .setAuthScopeType("ORGANIZATION")  // placeholder — actual orgId set on complete
                          .setAuthScopeId("__pending__")
                          .setAdditionalClaims(claims);
                  return Future.fromCompletionStage(pendingRegistrationService.create(pending));
              })
              .onSuccess(pending -> redirectToCompleteOrg(ctx, pending.getVerificationToken()))
              .onFailure(ex -> {
                  Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                  if (cause instanceof AccountExistsException) {
                      redirectToError(ctx, "account_exists");
                  } else {
                      log.warn("Signup resolution failed: {}", cause.getMessage());
                      redirectToError(ctx, "signup_failed");
                  }
              });
    }

    // ── /api/signup/complete-org ──────────────────────────────────────────────

    private void handleCompleteOrg(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String token = body == null ? null : body.getString("token");
        String orgName = body == null ? null : body.getString("orgName");
        String orgDescription = body == null ? null : body.getString("orgDescription");

        if (token == null || token.isBlank()) {
            respondError(ctx, 400, "token is required");
            return;
        }
        if (orgName == null || orgName.isBlank()) {
            respondError(ctx, 400, "orgName is required");
            return;
        }

        Future.fromCompletionStage(pendingRegistrationService.completeWithNewOrg(token, orgName, orgDescription))
              .onSuccess(user -> {
                  String jwt = mintTicket(user);
                  ctx.response().putHeader("Content-Type", "application/json")
                     .end(new JsonObject().put("token", jwt).put("orgId", user.getAuthScopeId()).encode());
              })
              .onFailure(ex -> {
                  Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                  respondError(ctx, 400, cause.getMessage());
              });
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String callbackUrl(String configId) {
        String base = gatewayProperties.resolveApiBaseUrl();
        if (base == null || base.isBlank()) {
            throw new IllegalStateException(
                    "kinotic.apiBaseUrl (or kinotic.appBaseUrl) is not configured — "
                    + "required for OIDC redirect_uri construction");
        }
        return base + "/api/signup/callback/" + configId;
    }

    private String mintTicket(IamUser user) {
        JsonObject claims = new JsonObject()
                .put("sub", user.getId())
                .put("email", user.getEmail())
                .put("authScopeType", user.getAuthScopeType())
                .put("authScopeId", user.getAuthScopeId());
        return jwtIssuer.sign(claims, new JWTOptions().setExpiresInSeconds(JWT_TTL_SECONDS));
    }

    private void redirectToCompleteOrg(RoutingContext ctx, String token) {
        String path = OidcConstants.REGISTER_PATH;
        ctx.response().setStatusCode(302)
           .putHeader("Location", path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8))
           .end();
    }

    private void redirectToError(RoutingContext ctx, String errorCode) {
        // Errors during signup go back to the login page so the frontend can show the right
        // message ("account exists, log in" etc.). Kept consistent with login-side error UX.
        String path = OidcConstants.LOGIN_ERROR_PATH;
        ctx.response().setStatusCode(302)
           .putHeader("Location", path + "?error=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8))
           .end();
    }

    private void respondError(RoutingContext ctx, int status, String message) {
        ctx.response().setStatusCode(status).putHeader("Content-Type", "application/json")
           .end(new JsonObject().put("error", message).encode());
    }

    /**
     * @see LoginHandler#flattenClaims(User) — same Vert.x quirk; canonical claims live on
     * {@code attributes.idToken}, not on the principal.
     */
    private static Map<String, Object> flattenClaims(User user) {
        Map<String, Object> map = new HashMap<>();
        JsonObject attrs = user.attributes();
        if (attrs != null) {
            JsonObject idToken = attrs.getJsonObject("idToken");
            if (idToken != null) idToken.forEach(e -> map.put(e.getKey(), e.getValue()));
            attrs.forEach(e -> {
                if (!"idToken".equals(e.getKey()) && !map.containsKey(e.getKey())) {
                    map.put(e.getKey(), e.getValue());
                }
            });
        }
        return map;
    }

    private static String stringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value == null ? null : value.toString();
    }

    private static Boolean booleanClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.valueOf(s);
        return null;
    }

    private static String firstPresent(Map<String, Object> claims, String... names) {
        for (String name : names) {
            String value = stringClaim(claims, name);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static class AccountExistsException extends RuntimeException {}
}
