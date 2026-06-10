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
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.gateway.internal.endpoints.rest.OidcConstants;
import org.kinotic.domain.api.model.iam.BaseOidcConfiguration;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.internal.utils.DomainUtil;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared response shaping + URL/route plumbing for every login/signup handler —
 * browser-session establishment, redirect construction, JSON error/payload writing, the
 * standard "after-callback" flow, and absolute URL building.
 * Each individual handler delegates the boilerplate here so its body keeps only the
 * route-specific decisions (which config to start with, which IamUser lookup to run).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEndpointSupport {

    private final KinoticDomainProperties domainProperties;


    /**
     * Builds an absolute backend URL ({@code kinotic.domain.apiBaseUrl}, falling back to
     * {@code appBaseUrl}, + {@code relativePath}) — used for OIDC {@code redirect_uri}s.
     */
    public String absoluteUrl(String relativePath) {
        return domainProperties.getDomain().resolveApiBaseUrl() + relativePath;
    }

    /**
     * Builds an absolute SPA URL ({@code kinotic.domain.appBaseUrl} + {@code relativePath}). The SPA is
     * a different origin than this gateway, so redirects back to the browser must be absolute.
     */
    public String appUrl(String relativePath) {
        return domainProperties.getDomain().getAppBaseUrl() + relativePath;
    }

    // ── Browser session login ─────────────────────────────────────────────────

    /**
     * Authenticates the browser by placing the logged-in user's {@link Participant} into the
     * Vert.x session. The subsequent STOMP WebSocket handshake reads it back from the session,
     * so the browser is authenticated by its session cookie and never handles a token. The
     * participant type follows the user's scope (org users get an OrganizationParticipant,
     * app users an ApplicationParticipant), which is what scopes their authority.
     */
    public void establishSession(RoutingContext ctx, IamUser user) {
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

    // ── Redirects ─────────────────────────────────────────────────────────────

    /**
     * Establishes the browser session and redirects to the SPA. No token travels in the URL —
     * the browser is authenticated by its session cookie.
     */
    public void redirectSuccess(RoutingContext ctx, IamUser user) {
        establishSession(ctx, user);
        ctx.response().setStatusCode(302)
           .putHeader("Location", appUrl("/"))
           .end();
    }

    /** {@code 302 Location: <appBaseUrl><errorPath>?error=<code>}. */
    public void redirectError(RoutingContext ctx, String errorCode) {
        ctx.response().setStatusCode(302)
           .putHeader("Location", appUrl("/login")
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
        ctx.response().putHeader("Content-Type", "application/json").end(providersJson(configs).encode());
    }

    /** The {@code [{id, name, provider}]} array behind {@link #respondProvidersList}, for embedding in larger payloads. */
    public JsonArray providersJson(List<? extends BaseOidcConfiguration> configs) {
        JsonArray arr = new JsonArray();
        for (BaseOidcConfiguration c : configs) {
            arr.add(new JsonObject()
                    .put("id", c.getId())
                    .put("name", c.getName())
                    .put("provider", c.getProvider() == null ? null : c.getProvider().key()));
        }
        return arr;
    }

    // ── Composite flows ───────────────────────────────────────────────────────

    /**
     * Standard email/password login endpoint: parses the JSON body, validates fields,
     * runs the supplied authenticate function, and on success establishes the browser
     * session — otherwise writes a generic {@code 401}. The handler only needs to provide
     * the authenticate call (already scope-aware where appropriate).
     */
    public void handlePasswordLogin(RoutingContext ctx,
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
                  log.warn("Password login error: {}", err.getMessage());
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
                      redirectError(ctx, "no_account");
                  } else if (!user.isEnabled()) {
                      redirectError(ctx, "account_disabled");
                  } else {
                      redirectSuccess(ctx, user);
                  }
              })
              .onFailure(err -> {
                  log.warn("Login resolution failed: {}", err.getMessage());
                  redirectError(ctx, "lookup_failed");
              });
    }
}
