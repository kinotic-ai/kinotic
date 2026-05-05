package org.kinotic.gateway.internal.auth;

import io.vertx.core.Future;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.gateway.api.config.KinoticApiGatewayProperties;
import org.kinotic.gateway.internal.endpoints.rest.OidcConstants;
import org.kinotic.os.api.model.iam.BaseOidcConfiguration;
import org.kinotic.os.api.model.iam.IamUser;
import org.kinotic.os.internal.api.services.iam.KinoticJwtIssuer;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Shared response shaping for every login/signup handler — JWT minting, redirect
 * construction, JSON error/payload writing, and the standard "after-callback" flow.
 * Each individual handler delegates the boilerplate here so its body keeps only the
 * route-specific decisions (which config to start with, which IamUser lookup to run).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginResponses {

    /** JWT TTL for the STOMP-CONNECT ticket — long enough for the browser to open the WebSocket. */
    public static final int JWT_TTL_SECONDS = 60;

    private final KinoticApiGatewayProperties gatewayProperties;
    private final KinoticJwtIssuer jwtIssuer;

    /**
     * Validated API base URL. Required for OIDC redirect_uri construction; throws so
     * misconfigurations surface at the first request rather than producing a malformed
     * redirect that the IdP will reject.
     */
    public String apiBase() {
        String base = gatewayProperties.resolveApiBaseUrl();
        if (base == null || base.isBlank()) {
            throw new IllegalStateException(
                    "kinotic.apiBaseUrl (or kinotic.appBaseUrl) is not configured — "
                    + "required for OIDC redirect_uri construction");
        }
        return base;
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

    /** {@code 200 application/json {"token":"<jwt>"}}. */
    public void respondJwt(RoutingContext ctx, IamUser user) {
        ctx.response().putHeader("Content-Type", "application/json")
           .end(new JsonObject().put("token", mintJwt(user)).encode());
    }

    /** {@code 200 application/json} with {@code token} plus extra fields the caller wants in the body. */
    public void respondJwt(RoutingContext ctx, IamUser user, JsonObject extras) {
        JsonObject body = new JsonObject().put("token", mintJwt(user));
        if (extras != null) extras.forEach(e -> body.put(e.getKey(), e.getValue()));
        ctx.response().putHeader("Content-Type", "application/json").end(body.encode());
    }

    // ── Redirects ─────────────────────────────────────────────────────────────

    /**
     * {@code 302 Location: <successPath>#token=<jwt>}. The fragment never appears in
     * access logs and isn't sent on subsequent requests, so the JWT stays out of
     * server-side telemetry.
     */
    public void redirectSuccess(RoutingContext ctx, IamUser user) {
        String jwt = mintJwt(user);
        ctx.response().setStatusCode(302)
           .putHeader("Location", OidcConstants.LOGIN_SUCCESS_PATH
                   + "#token=" + URLEncoder.encode(jwt, StandardCharsets.UTF_8))
           .end();
    }

    /** {@code 302 Location: <errorPath>?error=<code>}. */
    public void redirectError(RoutingContext ctx, String errorCode) {
        ctx.response().setStatusCode(302)
           .putHeader("Location", OidcConstants.LOGIN_ERROR_PATH
                   + "?error=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8))
           .end();
    }

    /**
     * Maps an OIDC callback failure to the right error redirect. {@link OidcCallbackException}
     * carries a typed code; everything else gets logged and falls through to {@code "exchange_failed"}.
     */
    public void redirectCallbackFailure(RoutingContext ctx, Throwable ex) {
        if (ex instanceof OidcCallbackException oce) {
            redirectError(ctx, oce.getErrorCode());
        } else {
            log.warn("OIDC callback failed: {}", ex.getMessage());
            redirectError(ctx, "exchange_failed");
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
     * Standard email/password token endpoint: parses the JSON body, validates fields,
     * runs the supplied authenticate function, and writes either {@code {token}} or a
     * generic {@code 401}. The handler only needs to provide the authenticate call
     * (already scope-aware where appropriate).
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
                  respondJwt(ctx, user);
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
    public Future<Void> completeOidcLogin(RoutingContext ctx,
                                          BaseOidcConfiguration config,
                                          Map<String, Object> claims,
                                          Function<String, CompletionStage<IamUser>> userLookup) {
        String sub = OidcFlowOrchestrator.stringClaim(claims, "sub");
        if (sub == null) {
            redirectError(ctx, "invalid_token");
            return Future.succeededFuture();
        }
        if (!OAuth2AuthFactory.isEmailVerified(claims, config.getProvider())) {
            redirectError(ctx, "email_not_verified");
            return Future.succeededFuture();
        }
        return Future.fromCompletionStage(userLookup.apply(sub))
                     .<Void>map(user -> {
                         if (user == null) {
                             redirectError(ctx, "no_account");
                         } else if (!user.isEnabled()) {
                             redirectError(ctx, "account_disabled");
                         } else {
                             redirectSuccess(ctx, user);
                         }
                         return null;
                     })
                     .otherwise(err -> {
                         log.warn("Login resolution failed: {}", err.getMessage());
                         redirectError(ctx, "lookup_failed");
                         return null;
                     });
    }
}
