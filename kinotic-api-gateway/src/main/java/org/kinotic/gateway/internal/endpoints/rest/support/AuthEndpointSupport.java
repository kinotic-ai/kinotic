package org.kinotic.gateway.internal.endpoints.rest.support;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.core.api.security.Participant;
import org.kinotic.gateway.api.config.KinoticApiGatewayProperties;
import org.kinotic.gateway.internal.endpoints.rest.OidcConstants;
import org.kinotic.domain.api.model.iam.BaseOidcConfiguration;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.internal.utils.DomainUtil;
import org.kinotic.os.internal.api.services.iam.KinoticJwtIssuer;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared response shaping + URL/route plumbing for every login/signup handler — JWT
 * minting, redirect construction, JSON error/payload writing, the standard
 * "after-callback" flow, session-handler installation, and absolute URL building.
 * Each individual handler delegates the boilerplate here so its body keeps only the
 * route-specific decisions (which config to start with, which IamUser lookup to run).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEndpointSupport {

    /** Access-token TTL for the CLI device/refresh token endpoints. */
    public static final int JWT_TTL_SECONDS = 60;

    private final KinoticApiGatewayProperties gatewayProperties;
    private final KinoticJwtIssuer jwtIssuer;


    /**
     * Builds an absolute backend URL ({@code kinotic.apiBaseUrl}, falling back to
     * {@code appBaseUrl}, + {@code relativePath}) — used for OIDC {@code redirect_uri}s.
     */
    public String absoluteUrl(String relativePath) {
        return gatewayProperties.resolveApiBaseUrl() + relativePath;
    }

    /**
     * Builds an absolute SPA URL ({@code kinotic.appBaseUrl} + {@code relativePath}). The SPA is
     * a different origin than this gateway, so redirects back to the browser must be absolute.
     */
    public String appUrl(String relativePath) {
        return gatewayProperties.getAppBaseUrl() + relativePath;
    }

    // ── JWT ───────────────────────────────────────────────────────────────────

    /** Mints the short-TTL Kinotic JWT carrying {@code sub/email/authScopeType/authScopeId}. */
    public String mintJwt(IamUser user) {
        JsonObject claims = new JsonObject()
                .put("sub", user.getId())
                .put("email", user.getEmail())
                .put("authScopeType", user.getAuthScopeType())
                .put("authScopeId", user.getAuthScopeId());
        return jwtIssuer.sign(claims, new JWTOptions().setExpiresInSeconds(JWT_TTL_SECONDS));
    }

    // ── Browser session login ─────────────────────────────────────────────────

    /**
     * Authenticates the browser by placing the logged-in user's {@link Participant} into the
     * Vert.x session. The subsequent STOMP WebSocket handshake reads it back from the session,
     * so the browser is authenticated by its session cookie and never handles a token.
     */
    private void establishSession(RoutingContext ctx, IamUser user) {
        Session session = ctx.session();
        // Rotate the session id on the privilege change so a pre-auth (possibly fixed)
        // id cannot be reused to ride the now-authenticated session.
        session.regenerateId();
        Participant participant = DomainUtil.createParticipant(user);
        ConnectedInfo connectedInfo = new ConnectedInfo();
        connectedInfo.setParticipant(participant);
        session.put(ConnectedInfo.SESSION_KEY, connectedInfo);
    }

    /** Establishes the browser session for {@code user} and writes {@code 204 No Content}. */
    public void respondSuccess(RoutingContext ctx, IamUser user) {
        establishSession(ctx, user);
        ctx.response().setStatusCode(204).end();
    }

    /**
     * {@code 200 application/json} with the OAuth token-pair shape consumed by the CLI:
     * a short-TTL {@code access_token} plus the {@code refresh_token} the client persists
     * to mint future access tokens.
     */
    public void respondTokenPair(RoutingContext ctx, IamUser user, String refreshToken) {
        JsonObject body = new JsonObject()
                .put("access_token", mintJwt(user))
                .put("token_type", "Bearer")
                .put("expires_in", JWT_TTL_SECONDS)
                .put("refresh_token", refreshToken);
        ctx.response().putHeader("Content-Type", "application/json").end(body.encode());
    }

    /**
     * Validates the {@code Bearer} token on the request and resolves the authenticated
     * user's id (the JWT {@code sub} claim). The returned future fails when the header is
     * missing or malformed, or when the token is invalid or expired.
     */
    public Future<String> requireAuthenticatedUserId(RoutingContext ctx) {
        String header = ctx.request().getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Future.failedFuture(new SecurityException("Missing or malformed Authorization header"));
        }
        String token = header.substring(7).trim();
        return jwtIssuer.authenticate(token)
                        .map(authenticated -> authenticated.principal().getString("sub"));
    }

    // ── Redirects ─────────────────────────────────────────────────────────────

    /**
     * Establishes the browser session and redirects to the SPA. No token travels in the URL —
     * the browser is authenticated by its session cookie.
     */
    public void redirectSuccess(RoutingContext ctx, IamUser user) {
        establishSession(ctx, user);
        ctx.response().setStatusCode(302)
           .putHeader("Location", appUrl(OidcConstants.LOGIN_SUCCESS_PATH))
           .end();
    }

    /** {@code 302 Location: <appBaseUrl><errorPath>?error=<code>}. */
    public void redirectError(RoutingContext ctx, String errorCode) {
        ctx.response().setStatusCode(302)
           .putHeader("Location", appUrl(OidcConstants.LOGIN_ERROR_PATH)
                   + "?error=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8))
           .end();
    }

    /**
     * Maps an OIDC callback failure to the right error redirect. {@link OidcCallbackException}
     * carries a typed code; everything else gets logged and falls through to
     * {@link OidcConstants#ERR_EXCHANGE_FAILED}.
     */
    public void redirectCallbackFailure(RoutingContext ctx, Throwable ex) {
        if (ex instanceof OidcCallbackException oce) {
            redirectError(ctx, oce.getErrorCode());
        } else {
            log.warn("OIDC callback failed: {}", ex.getMessage());
            redirectError(ctx, OidcConstants.ERR_EXCHANGE_FAILED);
        }
    }

    // ── JSON responses ────────────────────────────────────────────────────────

    /** {@code <status> application/json {"error":"<message>"}}. */
    public void respondError(RoutingContext ctx, int status, String message) {
        ctx.response().setStatusCode(status).putHeader("Content-Type", "application/json")
           .end(new JsonObject().put("error", message).encode());
    }

    /** Login-lookup path-fork response indicating the frontend should reveal the password field. */
    public Future<Void> respondPasswordPath(RoutingContext ctx) {
        ctx.response().putHeader("Content-Type", "application/json")
           .end(new JsonObject().put("type", "password").encode());
        return Future.succeededFuture();
    }

    /** Login-lookup path-fork response carrying the IdP authorize URL the frontend should redirect to. */
    public Future<Void> respondSsoRedirect(RoutingContext ctx, String redirectUrl) {
        ctx.response().putHeader("Content-Type", "application/json")
           .end(new JsonObject().put("type", "sso").put("redirect", redirectUrl).encode());
        return Future.succeededFuture();
    }

    /** Standard {@code [{id, name, provider}]} shape for "list of OIDC configs to choose from". */
    public void respondProvidersList(RoutingContext ctx, List<? extends BaseOidcConfiguration> configs) {
        JsonArray arr = new JsonArray();
        for (BaseOidcConfiguration c : configs) {
            arr.add(new JsonObject()
                    .put("id", c.getId())
                    .put("name", c.getName())
                    .put("provider", c.getProvider() == null ? null : c.getProvider().key()));
        }
        ctx.response().putHeader("Content-Type", "application/json").end(arr.encode());
    }

    // ── Composite flows ───────────────────────────────────────────────────────

    /**
     * Standard email/password login endpoint: parses the JSON body, validates fields,
     * runs the supplied authenticate function, and on success establishes the browser
     * session — otherwise writes a generic {@code 401}. The handler only needs to provide
     * the authenticate call (already scope-aware where appropriate).
     */
    public void handlePasswordToken(RoutingContext ctx,
                                    BiFunction<String, String, CompletionStage<IamUser>> authenticate) {
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
        Future.fromCompletionStage(authenticate.apply(email, password))
              .onSuccess(user -> {
                  if (user == null) {
                      // Generic 401 — covers unknown email, wrong password, OIDC user, disabled.
                      respondError(ctx, 401, "Invalid credentials");
                      return;
                  }
                  respondSuccess(ctx, user);
              })
              .onFailure(err -> {
                  log.warn("Token endpoint error: {}", err.getMessage());
                  respondError(ctx, 401, "Invalid credentials");
              });
    }

    /**
     * "After the IdP returned" composite flow used by every login callback: validates
     * {@code sub} + {@code email_verified}, looks up the {@link IamUser} via the
     * supplied function, and redirects success or error accordingly. Never creates
     * users — the signup path owns provisioning.
     *
     * @param userLookup takes the OIDC {@code sub} claim and returns the IamUser (or null).
     */
    public void completeOidcLogin(RoutingContext ctx,
                                  BaseOidcConfiguration config,
                                  Map<String, Object> claims,
                                  Function<String, CompletionStage<IamUser>> userLookup) {
        String sub = OAuth2Util.stringClaim(claims, "sub");
        if (sub == null) {
            redirectError(ctx, OidcConstants.ERR_INVALID_TOKEN);
            return;
        }
        if (!OAuth2Util.isEmailVerified(claims, config.getProvider())) {
            redirectError(ctx, OidcConstants.ERR_EMAIL_NOT_VERIFIED);
            return;
        }
        Future.fromCompletionStage(userLookup.apply(sub))
              .onSuccess(user -> {
                  if (user == null) {
                      redirectError(ctx, OidcConstants.ERR_NO_ACCOUNT);
                  } else if (!user.isEnabled()) {
                      redirectError(ctx, OidcConstants.ERR_ACCOUNT_DISABLED);
                  } else {
                      redirectSuccess(ctx, user);
                  }
              })
              .onFailure(err -> {
                  log.warn("Login resolution failed: {}", err.getMessage());
                  redirectError(ctx, OidcConstants.ERR_LOOKUP_FAILED);
              });
    }
}
