package org.kinotic.gateway.internal.endpoints.rest;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
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
import org.kinotic.os.internal.api.services.iam.KinoticJwtIssuer;
import org.kinotic.gateway.api.config.KinoticApiGatewayProperties;
import org.kinotic.gateway.internal.auth.OAuth2AuthFactory;
import org.kinotic.gateway.internal.auth.OAuth2AuthRegistry;
import org.kinotic.os.api.model.iam.IamUser;
import org.kinotic.os.api.model.iam.OidcConfiguration;
import org.kinotic.os.api.model.iam.OidcProviderKind;
import org.kinotic.os.api.services.KinoticSystemService;
import org.kinotic.os.api.services.OrganizationService;
import org.kinotic.os.api.services.iam.IamUserService;
import org.kinotic.os.api.services.iam.LocalAuthenticationService;
import org.kinotic.os.api.services.iam.OidcConfigurationService;
import org.kinotic.os.api.services.iam.PendingRegistrationService;
import org.kinotic.os.internal.api.model.iam.IamCredential;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Login routes for the kinotic-server. Every browser path converges on a short-TTL,
 * Kinotic-signed JWT that the frontend uses as the {@code Bearer} on STOMP CONNECT —
 * the SPA never sends raw credentials over the WebSocket.
 *
 * <h3>Three entry points</h3>
 * <ul>
 *   <li><b>Social button</b>: {@code POST /api/login/start/:provider} — picks the platform OIDC
 *       {@link OidcConfiguration} for that provider key (resolved via
 *       {@link KinoticSystemService#getOidcConfigurations()}) and redirects to the IdP.</li>
 *   <li><b>Email lookup → SSO redirect</b>: {@code POST /api/login/lookup {email}} — when the
 *       user's primary-org membership is OIDC and that org's SSO config is live, returns
 *       {@code {type: "sso", redirect: "..."}} (and stages state/nonce/PKCE on the session
 *       cookie). Otherwise returns {@code {type: "password"}} so the frontend can collect the
 *       password and complete via {@code /api/login/token}. Deliberately ambiguous on
 *       unknown email / local user / dead SSO config to avoid leaking which orgs use SSO.</li>
 *   <li><b>Email + password → token</b>: {@code POST /api/login/token {email, password}} —
 *       verifies bcrypt against {@link IamCredential}, resolves the user's primary
 *       {@link IamUser}, mints the same Kinotic JWT the OIDC paths produce, and returns
 *       {@code {token}}. Generic {@code 401} for any failure.</li>
 * </ul>
 *
 * <p>The two OIDC entry points converge on {@code GET /api/login/callback/:configId}: validates
 * state, exchanges the code, and looks up an existing {@link IamUser} by
 * {@code (oidcSubject, oidcConfigId)}. Missing user → 302 to {@code /login?error=no_account}.
 * Success → mint Kinotic JWT and 302 to the success URL with {@code #token=<jwt>} in the
 * fragment (fragments are not sent in browser requests, so the JWT never appears in access
 * logs). The token-endpoint path returns the same JWT shape as a JSON response.
 *
 * <p>{@code GET /api/login/providers} returns the unique provider keys from
 * {@code KinoticSystem.oidcConfigurationIds} for rendering the social buttons (no orgId —
 * these are platform-level). {@code POST /api/register/complete} consumes
 * a {@link org.kinotic.os.api.model.iam.PendingRegistration} from the
 * {@link org.kinotic.os.api.model.iam.UserProvisioningMode#REGISTRATION_REQUIRED} signup path.
 *
 * <p>Direct STOMP CONNECT with {@code login}/{@code passcode}/{@code authScopeType}/{@code
 * authScopeId} headers stays available for non-UI clients (CLI, automation) that already know
 * the target scope. The browser SPA does not use that path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginHandler {

    /** JWT TTL for the STOMP-CONNECT ticket — just long enough for the browser to open the WebSocket. */
    private static final int JWT_TTL_SECONDS = 60;

    // Session keys
    private static final String S_STATE = "oidc.state";
    private static final String S_NONCE = "oidc.nonce";
    private static final String S_PKCE = "oidc.pkce";
    private static final String S_CONFIG_ID = "oidc.configId";
    /** Set on the SSO path so the callback can confirm the resolved IamUser scope. Null for platform-social logins. */
    private static final String S_ORG_ID = "oidc.orgId";

    private final Vertx vertx;
    private final KinoticApiGatewayProperties gatewayProperties;
    private final IamUserService iamUserService;
    private final OrganizationService organizationService;
    private final OidcConfigurationService oidcConfigurationService;
    private final KinoticSystemService kinoticSystemService;
    private final LocalAuthenticationService localAuthenticationService;
    private final OAuth2AuthRegistry oauth2AuthRegistry;
    private final PendingRegistrationService pendingRegistrationService;
    private final KinoticJwtIssuer jwtIssuer;

    public void mountRoutes(Router router) {
        // BodyHandler is already installed at /api/* by SignUpHandler; not duplicated here.
        router.route("/api/login/*").handler(RedirectFlowSessionSupport.newSessionHandler(vertx));

        router.get("/api/login/providers").handler(this::handleProviders);
        router.post("/api/login/lookup").handler(this::handleLookup);
        router.post("/api/login/token").handler(this::handleToken);
        router.post("/api/login/start/:provider").handler(this::handleSocialStart);
        router.get("/api/login/callback/:configId").handler(this::handleCallback);
        router.post("/api/register/complete").handler(this::handleRegisterComplete);
    }

    // ── /api/login/token ──────────────────────────────────────────────────────

    private void handleToken(RoutingContext ctx) {
        JsonObject body;
        try {
            body = ctx.body().asJsonObject();
        } catch (Exception e) {
            respondError(ctx, 400, "Invalid request body");
            return;
        }
        String email = body == null ? null : body.getString("email");
        String password = body == null ? null : body.getString("password");
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            respondError(ctx, 400, "email and password are required");
            return;
        }

        Future.fromCompletionStage(localAuthenticationService.authenticateLocal(email, password))
              .onSuccess(user -> {
                  if (user == null) {
                      // Generic 401 — covers unknown email, wrong password, OIDC user, disabled.
                      respondError(ctx, 401, "Invalid credentials");
                      return;
                  }
                  String jwt = mintJwt(user);
                  ctx.response().putHeader("Content-Type", "application/json")
                     .end(new JsonObject().put("token", jwt).encode());
              })
              .onFailure(err -> {
                  log.warn("Token endpoint error: {}", err.getMessage());
                  respondError(ctx, 401, "Invalid credentials");
              });
    }

    // ── /api/login/providers ──────────────────────────────────────────────────

    private void handleProviders(RoutingContext ctx) {
        Future.fromCompletionStage(kinoticSystemService.getOidcConfigurations())
              .onSuccess(configs -> {
                  JsonArray providers = new JsonArray();
                  java.util.Set<String> seen = new java.util.LinkedHashSet<>();
                  for (OidcConfiguration c : configs) {
                      if (c.getProvider() == null) continue;
                      String key = c.getProvider().key();
                      if (seen.add(key)) providers.add(key);
                  }
                  ctx.response().putHeader("Content-Type", "application/json").end(providers.encode());
              })
              .onFailure(err -> {
                  log.warn("Failed to list platform providers: {}", err.getMessage());
                  respondError(ctx, 500, "Failed to list providers");
              });
    }

    // ── /api/login/lookup ─────────────────────────────────────────────────────

    private void handleLookup(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String email = body == null ? null : body.getString("email");
        if (email == null || email.isBlank()) {
            respondError(ctx, 400, "email is required");
            return;
        }

        Future.fromCompletionStage(iamUserService.findByEmailPrimary(email))
              .compose(user -> resolveSsoOrPassword(ctx, user))
              .onFailure(err -> {
                  log.warn("Login lookup failed for {}: {}", email, err.getMessage());
                  respondError(ctx, 500, "Lookup failed");
              });
    }

    /**
     * Returns the JSON body for the lookup. If the user is missing, local, or has a dead
     * OIDC config → "password" (frontend reveals password field; STOMP login surfaces
     * invalid creds on failure). If OIDC and the org config is live → "sso" with a
     * pre-built redirect URL; the session cookie is set so the callback can validate state.
     */
    private Future<Void> resolveSsoOrPassword(RoutingContext ctx, IamUser user) {
        if (user == null || user.getAuthType() == null) {
            return respondPasswordPath(ctx);
        }
        // Only ORGANIZATION-scope OIDC users can have an org SSO config; everything else → password.
        if (!"OIDC".equals(user.getAuthType().name())
                || user.getOidcConfigId() == null
                || !"ORGANIZATION".equals(user.getAuthScopeType())) {
            return respondPasswordPath(ctx);
        }

        String orgId = user.getAuthScopeId();
        String configId = user.getOidcConfigId();

        return Future.fromCompletionStage(organizationService.getOidcConfigurations(orgId))
                     .compose(orgConfigs -> {
            OidcConfiguration match = null;
            for (OidcConfiguration c : orgConfigs) {
                if (configId.equals(c.getId())) { match = c; break; }
            }
            if (match == null) {
                // Org's SSO config was deactivated or removed — fall back to password (which
                // will fail with invalid creds since the user has no password). Generic.
                return respondPasswordPath(ctx);
            }
            return startSsoFlow(ctx, match, orgId);
        });
    }

    private Future<Void> respondPasswordPath(RoutingContext ctx) {
        ctx.response().putHeader("Content-Type", "application/json")
           .end(new JsonObject().put("type", "password").encode());
        return Future.succeededFuture();
    }

    /**
     * Generates state/PKCE/nonce, stores them on the session, builds the IdP authorize URL,
     * and returns it to the frontend in {@code {type: "sso", redirect: "..."}}. The frontend
     * does {@code window.location = redirect}; the browser carries the just-set session
     * cookie back when the IdP redirects to our callback.
     */
    private Future<Void> startSsoFlow(RoutingContext ctx, OidcConfiguration config, String orgId) {
        return prepareAuthorizeUrl(ctx, config, orgId).map(url -> {
            ctx.response().putHeader("Content-Type", "application/json")
               .end(new JsonObject().put("type", "sso").put("redirect", url).encode());
            return null;
        });
    }

    // ── /api/login/start/:provider (social button) ────────────────────────────

    private void handleSocialStart(RoutingContext ctx) {
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
                  return prepareAuthorizeUrl(ctx, match, null)
                          .map(url -> {
                              ctx.response().setStatusCode(302).putHeader("Location", url).end();
                              return null;
                          });
              })
              .onFailure(ex -> {
                  log.error("Social login start failed for {}", provider, ex);
                  respondError(ctx, 500, "Provider initialization failed");
              });
    }

    /**
     * Shared state-and-URL setup for social and SSO flows. The {@code orgId} is null for
     * platform-social logins (callback will pick the user's default-org membership).
     */
    private Future<String> prepareAuthorizeUrl(RoutingContext ctx, OidcConfiguration config, String orgId) {
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
        if (orgId != null) {
            session.put(S_ORG_ID, orgId);
        }

        return oauth2AuthRegistry.get(config, config.getId()).map(oauth2 -> oauth2.authorizeURL(
                new OAuth2AuthorizationURL()
                        .setRedirectUri(callbackUrl(config.getId()))
                        .setScopes(List.of("openid", "email", "profile"))
                        .setState(state)
                        .setCodeChallenge(pkceChallenge)
                        .setCodeChallengeMethod("S256")
                        .putAdditionalParameter("nonce", nonce)));
    }

    // ── /api/login/callback/:configId ─────────────────────────────────────────

    private void handleCallback(RoutingContext ctx) {
        String pathConfigId = ctx.pathParam("configId");
        String code = ctx.request().getParam("code");
        String state = ctx.request().getParam("state");
        String idpError = ctx.request().getParam("error");

        if (idpError != null) {
            log.info("OIDC callback error from config {}: {}", pathConfigId, idpError);
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
        String orgId = session.get(S_ORG_ID);  // may be null for platform-social
        // Consume the session regardless of outcome.
        session.destroy();

        if (expectedState == null || !expectedState.equals(state)
                || sessionConfigId == null || !sessionConfigId.equals(pathConfigId)) {
            log.warn("OIDC callback state mismatch for configId={}", pathConfigId);
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
              .onSuccess(entry -> resolveLogin(ctx, entry.getKey(), entry.getValue(), orgId))
              .onFailure(ex -> {
                  log.warn("OIDC callback failed: {}", ex.getMessage());
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
     * Login resolution: looks up the existing {@link IamUser} by {@code (sub, configId)}.
     * For SSO (orgId set) we narrow to that scope. For platform-social (orgId null) we
     * search across scopes and pick the user's default-org membership. Never creates users
     * — that's the signup path's job.
     */
    private void resolveLogin(RoutingContext ctx, OidcConfiguration config, User idpUser, String orgId) {
        Map<String, Object> claims = flattenClaims(idpUser);

        if (!OAuth2AuthFactory.isIssuerValid(claims, config.getAuthority())) {
            log.warn("OIDC issuer validation failed for config {}: iss={}, tid={}",
                     config.getId(), claims.get("iss"), claims.get("tid"));
            redirectToError(ctx, "invalid_token");
            return;
        }

        String sub = stringClaim(claims, "sub");

        if (sub == null) {
            redirectToError(ctx, "invalid_token");
            return;
        }
        if (!OAuth2AuthFactory.isEmailVerified(claims, config.getProvider())) {
            redirectToError(ctx, "email_not_verified");
            return;
        }

        Future<IamUser> lookup = orgId != null
                ? Future.fromCompletionStage(iamUserService.findByOidcIdentityAndScope(
                        sub, config.getId(), "ORGANIZATION", orgId))
                : Future.fromCompletionStage(iamUserService.findByOidcIdentity(sub, config.getId()))
                        .map(this::pickPrimary);

        lookup.onSuccess(user -> {
            if (user == null) {
                redirectToError(ctx, "no_account");
                return;
            }
            if (!user.isEnabled()) {
                redirectToError(ctx, "account_disabled");
                return;
            }
            String token = mintJwt(user);
            redirectSuccess(ctx, token);
        }).onFailure(err -> {
            log.warn("Login resolution failed: {}", err.getMessage());
            redirectToError(ctx, "lookup_failed");
        });
    }

    private IamUser pickPrimary(List<IamUser> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        for (IamUser u : candidates) {
            if (u.isPrimary()) return u;
        }
        // Fall back to first if no default flag is set yet (shouldn't happen post-signup).
        return candidates.getFirst();
    }

    // ── /api/register/complete ────────────────────────────────────────────────

    private void handleRegisterComplete(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String token = body == null ? null : body.getString("token");
        if (token == null || token.isBlank()) {
            respondError(ctx, 400, "token is required");
            return;
        }
        String displayNameOverride = body.getString("displayName");

        Future.fromCompletionStage(pendingRegistrationService.complete(token, user -> {
            if (displayNameOverride != null && !displayNameOverride.isBlank()) {
                user.setDisplayName(displayNameOverride);
            }
        })).onSuccess(user -> {
            String jwt = mintJwt(user);
            ctx.response().putHeader("Content-Type", "application/json")
               .end(new JsonObject().put("token", jwt).encode());
        }).onFailure(ex -> {
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
        return base + "/api/login/callback/" + configId;
    }

    private String mintJwt(IamUser user) {
        JsonObject claims = new JsonObject()
                .put("sub", user.getId())
                .put("email", user.getEmail())
                .put("authScopeType", user.getAuthScopeType())
                .put("authScopeId", user.getAuthScopeId());
        return jwtIssuer.sign(claims, new JWTOptions().setExpiresInSeconds(JWT_TTL_SECONDS));
    }

    private void redirectSuccess(RoutingContext ctx, String jwt) {
        String path = OidcConstants.LOGIN_SUCCESS_PATH;
        ctx.response().setStatusCode(302)
           .putHeader("Location", path + "#token=" + URLEncoder.encode(jwt, StandardCharsets.UTF_8))
           .end();
    }

    private void redirectToError(RoutingContext ctx, String errorCode) {
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
     * Extracts OIDC claims from a Vert.x {@link User}. Vert.x v5 puts the decoded id_token
     * claims under {@code user.attributes().getJsonObject("idToken")} — that's where {@code
     * iss}, {@code tid}, {@code sub}, {@code email}, etc. live. The principal itself only
     * carries the raw token-endpoint response (encoded JWT strings).
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

}
