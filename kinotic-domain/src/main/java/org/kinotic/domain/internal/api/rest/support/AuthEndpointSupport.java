package org.kinotic.domain.internal.api.rest.support;

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
import org.kinotic.domain.internal.api.rest.OidcErrorCodes;
import org.kinotic.domain.api.model.iam.BaseOidcConfiguration;
import org.kinotic.domain.api.model.iam.ParticipantIdentity;
import org.kinotic.domain.api.model.iam.UserParticipantIdentity;
import org.kinotic.domain.api.model.iam.KinoticAudience;
import org.kinotic.domain.api.model.iam.OidcProviderKind;
import org.kinotic.domain.api.services.iam.OrgSignupOidcConfigurationService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.services.iam.KinoticJwtIssuer;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;
import io.vertx.ext.auth.JWTOptions;
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
 * route-specific decisions (which config to start with, which UserParticipantIdentity lookup to run).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEndpointSupport {

    /** Access-token TTL for OAuth-issued tokens; clients refresh via the rotating refresh token. */
    private static final int ACCESS_TOKEN_TTL_SECONDS = 3600;

    /**
     * Session key holding the SPA path a completed social login returns to, placed by
     * {@link #handleSocialStart} and consumed by {@link #redirectSuccess}. Carried on the session
     * because IdP redirect URIs are registered exactly, so it cannot ride in the callback URL.
     */
    private static final String RETURN_PATH_SESSION_KEY = "loginReturnPath";

    private final KinoticDomainProperties domainProperties;
    private final KinoticJwtIssuer jwtIssuer;
    private final OrgSignupOidcConfigurationService orgSignupOidcConfigurationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;


    /**
     * Builds an absolute backend URL ({@code kinotic.domain.apiBaseUrl}, falling back to
     * {@code appBaseUrl}, + {@code relativePath}) — used for OIDC {@code redirect_uri}s.
     */
    public String absoluteUrl(String relativePath) {
        return domainProperties.getDomain().resolveApiBaseUrl() + relativePath;
    }

    /**
     * Builds an absolute URL on the OAuth 2.1 surface
     * ({@code kinotic.domain.oauth.issuerBaseUrl}, falling back to {@code apiBaseUrl}, +
     * {@code relativePath}) — used for the issuer identifier, the endpoints its metadata
     * advertises, and the RFC 9728 resource metadata an MCP host discovers.
     */
    // FIXME: shotgun surgery — one of five places that know the OAuth surface has its own base URL.
    // Every externally reached URL added from here on has to pick this over absoluteUrl, with
    // nothing enforcing the choice. See "OAuth base URL split" in docs/NavidNotes.md.
    public String issuerUrl(String relativePath) {
        return domainProperties.getDomain().resolveIssuerBaseUrl() + relativePath;
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
    public void establishSession(RoutingContext ctx, UserParticipantIdentity user) {
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
    public void respondSuccess(RoutingContext ctx, UserParticipantIdentity user) {
        establishSession(ctx, user);
        ctx.response().setStatusCode(204).end();
    }

    // ── Redirects ─────────────────────────────────────────────────────────────

    /**
     * Establishes the browser session and redirects to the SPA — to the path the login started
     * from when there was one, otherwise the SPA root. No token travels in the URL — the browser
     * is authenticated by its session cookie.
     */
    public void redirectSuccess(RoutingContext ctx, UserParticipantIdentity user) {
        // read before establishSession, which regenerates the session id
        String returnPath = ctx.session().remove(RETURN_PATH_SESSION_KEY);
        establishSession(ctx, user);
        ctx.response().setStatusCode(302)
           .putHeader("Location", appUrl(returnPath != null ? returnPath : "/"))
           .end();
    }

    /**
     * The SPA path a completed login returns to, or {@code null} when the request named none or
     * named one that is not a path within the SPA.
     */
    private static String safeReturnPath(String referer) {
        String ret = null;
        // concatenated onto appBaseUrl, so a value that could open an authority ("//host") or
        // break out of the Location header is dropped rather than sanitized
        if (referer != null && referer.startsWith("/") && !referer.startsWith("//")
                && referer.indexOf('\\') < 0 && referer.chars().noneMatch(Character::isISOControl)) {
            ret = referer;
        }
        return ret;
    }

    /** {@code 302 Location: <appBaseUrl><errorPath>?error=<code>}. */
    public void redirectError(RoutingContext ctx, String errorCode) {
        // the flow that stored a return path ends here, so it must not outlive it and send an
        // unrelated later login somewhere the user never asked for
        ctx.session().remove(RETURN_PATH_SESSION_KEY);
        ctx.response().setStatusCode(302)
           .putHeader("Location", appUrl("/login")
                   + "?error=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8))
           .end();
    }

    /**
     * Maps an OIDC callback failure to the right error redirect. {@link OidcCallbackException}
     * carries a typed code; everything else gets logged and falls through to
     * {@link OidcErrorCodes#EXCHANGE_FAILED}.
     */
    public void redirectCallbackFailure(RoutingContext ctx, Throwable ex) {
        if (ex instanceof OidcCallbackException oce) {
            redirectError(ctx, oce.getErrorCode());
        } else {
            log.warn("OIDC callback failed: {}", ex.getMessage());
            redirectError(ctx, OidcErrorCodes.EXCHANGE_FAILED);
        }
    }

    // ── JSON responses ────────────────────────────────────────────────────────

    /** {@code <status> application/json {"error":"<message>"}}. */
    public void respondError(RoutingContext ctx, int status, String message) {
        ctx.response().setStatusCode(status).putHeader("Content-Type", "application/json")
           .end(new JsonObject().put("error", message).encode());
    }

    // ── Request bodies ────────────────────────────────────────────────────────

    /**
     * The request body parsed as JSON, so the caller can read its fields directly. A body that is
     * absent or is not JSON answers the request with a {@code 400}.
     */
    public JsonObject readJsonBody(RoutingContext ctx) {
        JsonObject ret;
        try {
            ret = ctx.body().asJsonObject();
        } catch (Exception e) {
            // a malformed body raises DecodeException, which the router's failure handler renders as a
            // 500; IllegalArgumentException is what it maps to the 400 this deserves
            throw new IllegalArgumentException("Invalid request body", e);
        }
        if (ret == null) {
            throw new IllegalArgumentException("Invalid request body");
        }
        return ret;
    }

    // ── Token issuance ────────────────────────────────────────────────────────

    /**
     * Mints a Kinotic access-token JWT stamped for {@code audience}, carrying the identity's
     * {@code sub/organizationId/applicationId} (and {@code email} for a user). The {@code sub}
     * is what every entry point resolves the caller's
     * {@link org.kinotic.core.api.security.Participant} from, so the bearer acts as this
     * identity and no other.
     */
    private String mintAccessToken(ParticipantIdentity identity, KinoticAudience audience) {
        JsonObject claims = new JsonObject()
                .put("sub", identity.getId());
        if (identity instanceof UserParticipantIdentity user) {
            claims.put("email", user.getEmail());
        }
        if (identity.getOrganizationId() != null) {
            claims.put("organizationId", identity.getOrganizationId());
        }
        if (identity.getApplicationId() != null) {
            claims.put("applicationId", identity.getApplicationId());
        }
        return jwtIssuer.sign(claims, new JWTOptions().setExpiresInSeconds(ACCESS_TOKEN_TTL_SECONDS), audience);
    }

    /**
     * {@code 200 application/json} with an OAuth token pair stamped for {@code audience}: an
     * {@code access_token}, plus the {@code refresh_token} the client persists to mint future
     * access tokens. Both act as {@code identity}.
     */
    public void respondTokenPair(RoutingContext ctx, ParticipantIdentity identity, String refreshToken, KinoticAudience audience) {
        respondTokens(ctx, identity, refreshToken, audience);
    }

    /**
     * {@code 200 application/json} with an OAuth access token stamped for {@code audience} and
     * no refresh token — the client-credentials response shape (RFC 6749 §4.4.3): the client
     * holds a secret it re-authenticates with, so a refresh token has nothing to add.
     */
    public void respondAccessToken(RoutingContext ctx, ParticipantIdentity identity, KinoticAudience audience) {
        respondTokens(ctx, identity, null, audience);
    }

    private void respondTokens(RoutingContext ctx, ParticipantIdentity identity, String refreshToken, KinoticAudience audience) {
        String accessToken;
        try {
            accessToken = mintAccessToken(identity, audience);
        } catch (Exception e) {
            // Callers invoke this from a Future handler, where a throw never reaches the router's
            // failure handler and leaves the request unanswered until the client times out.
            log.error("Could not mint access token", e);
            respondError(ctx, 500, "Could not issue tokens");
            return;
        }
        JsonObject body = new JsonObject()
                .put("access_token", accessToken)
                .put("token_type", "Bearer")
                .put("expires_in", ACCESS_TOKEN_TTL_SECONDS);
        if (refreshToken != null) {
            body.put("refresh_token", refreshToken);
        }
        ctx.response().putHeader("Content-Type", "application/json")
           // RFC 6749 §5.1 — a token response must never be cached by an intermediary
           .putHeader("Cache-Control", "no-store")
           .putHeader("Pragma", "no-cache")
           .end(body.encode());
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
     * Standard social start endpoint: resolves the {@code :provider} path parameter to the enabled
     * Kinotic-curated configuration for that provider kind and redirects the browser to its IdP,
     * writing a {@code 400} for an unknown or disabled provider. The caller supplies the callback
     * URL the IdP returns to, which is what distinguishes a signup start from a login start.
     * <p>
     * A {@code referer} query parameter naming a path within the SPA is where the browser lands
     * once the flow completes.
     */
    public void handleSocialStart(RoutingContext ctx, Function<String, String> callbackUrl) {
        String provider = ctx.pathParam("provider");
        OidcProviderKind providerKind;
        try {
            providerKind = OidcProviderKind.fromKey(provider);
        } catch (IllegalArgumentException ex) {
            respondError(ctx, 400, "Unknown platform provider: " + provider);
            return;
        }

        String returnPath = safeReturnPath(ctx.request().getParam("referer"));

        Future.fromCompletionStage(orgSignupOidcConfigurationService.findEnabledByProvider(providerKind))
              .compose(config -> {
                  if (config == null) {
                      respondError(ctx, 400, "Unknown or disabled platform provider: " + provider);
                      return Future.succeededFuture();
                  }
                  return oidcFlowOrchestrator.startFlow(ctx, config, callbackUrl.apply(config.getId()), null);
              })
              .onSuccess(url -> {
                  // null means the compose above already answered the unknown-provider case
                  if (url != null) {
                      // written after startFlow, which regenerates the session id. each start
                      // defines where its own flow lands, so a path a previous one abandoned at
                      // the IdP is replaced rather than inherited
                      if (returnPath != null) {
                          ctx.session().put(RETURN_PATH_SESSION_KEY, returnPath);
                      } else {
                          ctx.session().remove(RETURN_PATH_SESSION_KEY);
                      }
                      ctx.response().setStatusCode(302).putHeader("Location", url).end();
                  }
              })
              .onFailure(ex -> {
                  log.error("Social start failed for provider {}", provider, ex);
                  respondError(ctx, 500, "Provider initialization failed");
              });
    }

    /**
     * Standard email/password login endpoint: parses the JSON body, validates fields,
     * runs the supplied authenticate function, and on success establishes the browser
     * session — otherwise writes a generic {@code 401}. The handler only needs to provide
     * the authenticate call (already scope-aware where appropriate).
     */
    public void handlePasswordLogin(RoutingContext ctx,
                                    BiFunction<String, String, CompletionStage<UserParticipantIdentity>> authenticate) {
        JsonObject body = readJsonBody(ctx);
        String email = body.getString("email");
        String password = body.getString("password");
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
     * {@code sub} + {@code email_verified}, looks up the {@link UserParticipantIdentity} via the
     * supplied function, and redirects success or error accordingly. Never creates
     * users — the signup path owns provisioning.
     *
     * @param userLookup takes the OIDC {@code sub} claim and returns the UserParticipantIdentity (or null).
     */
    public void completeOidcLogin(RoutingContext ctx,
                                  BaseOidcConfiguration config,
                                  Map<String, Object> claims,
                                  Function<String, CompletionStage<UserParticipantIdentity>> userLookup) {
        String sub = OAuth2Util.stringClaim(claims, "sub");
        if (sub == null) {
            redirectError(ctx, OidcErrorCodes.INVALID_TOKEN);
            return;
        }
        if (!OAuth2Util.isEmailVerified(claims, config.getProvider())) {
            redirectError(ctx, OidcErrorCodes.EMAIL_NOT_VERIFIED);
            return;
        }
        Future.fromCompletionStage(userLookup.apply(sub))
              .onSuccess(user -> {
                  if (user == null) {
                      redirectError(ctx, OidcErrorCodes.NO_ACCOUNT);
                  } else if (!user.isEnabled()) {
                      redirectError(ctx, OidcErrorCodes.ACCOUNT_DISABLED);
                  } else {
                      redirectSuccess(ctx, user);
                  }
              })
              .onFailure(err -> {
                  log.warn("Login resolution failed: {}", err.getMessage());
                  redirectError(ctx, OidcErrorCodes.LOOKUP_FAILED);
              });
    }
}
