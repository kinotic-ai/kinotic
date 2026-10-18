package org.kinotic.domain.api.rest.support;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SessionBinding;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OwnedOidcConfiguration;
import org.kinotic.domain.api.model.security.DelegateKind;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.KinoticAudience;
import org.kinotic.domain.api.model.security.OidcProviderKind;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.RefreshTokenService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.api.services.security.KinoticJwtIssuer;

import io.vertx.core.Future;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared response shaping + route plumbing for every login/signup handler —
 * browser-session establishment, redirects to the UI, JSON error/payload writing, OAuth token
 * issuance, and the standard "after-callback" flow.
 * Each individual handler delegates the boilerplate here so its body keeps only the
 * route-specific decisions (which config to start with, which UserParticipantIdentity lookup to run).
 * Each server subclasses it once, supplying {@link #uiUrl}: where its browser flows send the user.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AuthEndpointSupport {

    /** Access-token TTL for OAuth-issued tokens; clients refresh via the rotating refresh token. */
    private static final int ACCESS_TOKEN_TTL_SECONDS = 3600;

    /**
     * Session key holding the SPA path a completed social login returns to, placed by
     * {@link #handleSocialStart} and consumed by {@link #redirectSuccess}. Carried on the session
     * because IdP redirect URIs are registered exactly, so it cannot ride in the callback URL.
     */
    private static final String RETURN_PATH_SESSION_KEY = "loginReturnPath";

    private final KinoticJwtIssuer jwtIssuer;
    private final OidcConfigurationService oidcConfigurationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final ParticipantIdentityService identityService;
    private final RefreshTokenService refreshTokenService;


    // ── Browser session login ─────────────────────────────────────────────────

    /**
     * Authenticates the page at {@code origin} by binding the logged-in {@link Participant} to it in the
     * Vert.x session (see {@link SessionBinding}). The subsequent STOMP WebSocket handshake from that page
     * reads it back, so the browser is authenticated by its session cookie and never handles a token. The
     * participant's type is what scopes its authority: an account signs in as an OrganizationParticipant
     * or SystemParticipant through one of its memberships, an application's user as an
     * ApplicationParticipant.
     */
    public void establishSession(RoutingContext ctx, String origin, Participant participant) {
        // Rotate the session id on the privilege change so a pre-auth (possibly fixed)
        // id cannot be reused to ride the now-authenticated session.
        ctx.session().regenerateId();
        ConnectedInfo connectedInfo = new ConnectedInfo();
        connectedInfo.setParticipant(participant);
        SessionBinding.bind(ctx, origin, connectedInfo);
    }

    /**
     * Establishes the browser session for {@code participant}, bound to the page that sent the request,
     * and writes {@code 204 No Content}.
     */
    public void respondSuccess(RoutingContext ctx, Participant participant) {
        establishSession(ctx, SessionBinding.origin(ctx), participant);
        ctx.response().setStatusCode(204).end();
    }

    // ── Redirects ─────────────────────────────────────────────────────────────

    /**
     * Absolute URL of {@code path} on the UI this server's browser flows send the user to. Each server
     * implements it: the portal, the console, or the primary UI of the application the request is
     * addressed to.
     *
     * @param ctx  the request
     * @param path the path on that UI, beginning with {@code /}
     * @return the URL, or a failed future when the request has no UI to send the browser to
     */
    public abstract Future<String> uiUrl(RoutingContext ctx, String path);

    /**
     * Establishes the browser session, bound to the page at {@code origin} that started the
     * login, and redirects back to that page's origin (this server's UI when the start named no
     * page) — to the path the login started from when there was one, otherwise the root. No token
     * travels in the URL — the browser is authenticated by its session cookie.
     */
    public void redirectSuccess(RoutingContext ctx, String origin, Participant participant) {
        // read before establishSession, which regenerates the session id
        String returnPath = ctx.session().remove(RETURN_PATH_SESSION_KEY);
        establishSession(ctx, origin, participant);
        redirectToPage(ctx, origin, returnPath != null ? returnPath : "/");
    }

    /**
     * {@code 302 Location: <this server's UI><path>}, resolved through {@link #uiUrl}. A request with no
     * UI to send the browser to fails with the reason {@code uiUrl} gives.
     */
    public void redirectToUi(RoutingContext ctx, String path) {
        uiUrl(ctx, path)
                .onSuccess(url -> ctx.response().setStatusCode(302).putHeader("Location", url).end())
                .onFailure(ctx::fail);
    }

    /**
     * The SPA path a completed login returns to, or {@code null} when the request named none or
     * named one that is not a path within the SPA.
     */
    private static String safeReturnPath(String referer) {
        String ret = null;
        // concatenated onto the UI's URL, so a value that could open an authority ("//host") or
        // break out of the Location header is dropped rather than sanitized
        if (referer != null && referer.startsWith("/") && !referer.startsWith("//")
                && referer.indexOf('\\') < 0 && referer.chars().noneMatch(Character::isISOControl)) {
            ret = referer;
        }
        return ret;
    }

    /**
     * {@code 302 Location: <origin>/login?error=<code>}, the login page of the page at {@code origin}
     * that started the flow, or of this server's UI when {@code origin} is null.
     */
    public void redirectError(RoutingContext ctx, String origin, String errorCode) {
        redirectError(ctx, origin, errorCode, Map.of());
    }

    /**
     * {@link #redirectError(RoutingContext, String, String)} carrying {@code details} as further query
     * parameters of the login page, for an error the page can act on with more than its code.
     */
    public void redirectError(RoutingContext ctx, String origin, String errorCode, Map<String, String> details) {
        // the flow that stored a return path ends here, so it must not outlive it and send an
        // unrelated later login somewhere the user never asked for
        ctx.session().remove(RETURN_PATH_SESSION_KEY);
        StringBuilder path = new StringBuilder("/login?error=").append(URLEncoder.encode(errorCode, StandardCharsets.UTF_8));
        details.forEach((name, value) -> path.append('&').append(URLEncoder.encode(name, StandardCharsets.UTF_8))
                                             .append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8)));
        redirectToPage(ctx, origin, path.toString());
    }

    // the path on the page that started the flow, or on this server's UI when the flow named no page
    private void redirectToPage(RoutingContext ctx, String origin, String path) {
        if (origin != null) {
            ctx.response().setStatusCode(302).putHeader("Location", origin + path).end();
        } else {
            redirectToUi(ctx, path);
        }
    }

    /**
     * Maps an OIDC callback failure to the right error redirect, to this server's UI. {@link OidcCallbackException}
     * carries a typed code; everything else gets logged and falls through to
     * {@link OidcErrorCodes#EXCHANGE_FAILED}.
     */
    public void redirectCallbackFailure(RoutingContext ctx, Throwable ex) {
        // a callback that failed with no flow behind it names no starting page
        redirectCallbackFailure(ctx, null, ex);
    }

    /** {@link #redirectCallbackFailure(RoutingContext, Throwable)} to the login page of the page at {@code origin}. */
    public void redirectCallbackFailure(RoutingContext ctx, String origin, Throwable ex) {
        redirectError(ctx, origin, callbackErrorCode(ex));
    }

    /** The error code a failed callback surfaces: the typed code of an {@link OidcCallbackException}, else {@link OidcErrorCodes#EXCHANGE_FAILED}. */
    public String callbackErrorCode(Throwable ex) {
        String ret;
        if (ex instanceof OidcCallbackException oce) {
            ret = oce.getErrorCode();
        } else {
            log.warn("OIDC callback failed: {}", ex.getMessage());
            ret = OidcErrorCodes.EXCHANGE_FAILED;
        }
        return ret;
    }

    // ── JSON responses ────────────────────────────────────────────────────────

    /** {@code <status> application/json {"error":"<message>"}}. */
    public void respondError(RoutingContext ctx, int status, String message) {
        ctx.response().setStatusCode(status).putHeader("Content-Type", "application/json")
           .end(new JsonObject().put("error", message).encode());
    }

    // ── Request bodies ────────────────────────────────────────────────────────

    /**
     * The person signed in on the page that sent the request. A page without a login fails the
     * request with {@code 401}, and a login that is not a person with {@code 400}.
     */
    public Participant requireSessionUser(RoutingContext ctx) {
        ConnectedInfo connectedInfo = SessionBinding.connectedInfo(ctx);
        if (connectedInfo == null || connectedInfo.getParticipant() == null) {
            throw new AuthenticationException("Sign in to continue");
        }
        // consent must come from a person: a delegate approving grants could mint itself further
        // delegates on the user's behalf without the user ever seeing a consent screen
        DomainUtil.requireUserParticipant(connectedInfo.getParticipant());
        return connectedInfo.getParticipant();
    }

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
                .put("expires_in", ACCESS_TOKEN_TTL_SECONDS)
                .put("refresh_token", refreshToken);
        ctx.response().putHeader("Content-Type", "application/json")
           // RFC 6749 §5.1 — a token response must never be cached by an intermediary
           .putHeader("Cache-Control", "no-store")
           .putHeader("Pragma", "no-cache")
           .end(body.encode());
    }

    /**
     * Answers a token request for a delegate-minting grant: finds or creates {@code approver}'s
     * delegate of the given {@code kind} for the client {@code clientKey} in the scope the approver acts
     * in, issues a refresh token for the audience of {@code kind}, and responds with the token pair. Both
     * tokens act as the delegate. {@code sessionLabel} names the refresh-token family where the client
     * supplied one.
     */
    public Future<Void> issueDelegateTokens(RoutingContext ctx,
                                            Participant approver,
                                            DelegateKind kind,
                                            String clientKey,
                                            String clientName,
                                            String sessionLabel) {
        return identityService.findOrCreateDelegate(approver, kind, clientKey, clientName)
                .compose(delegate -> refreshTokenService.issue(delegate.getId(),
                                                               delegate.getDelegateKind().getAudience(),
                                                               sessionLabel)
                        .onSuccess(refreshToken -> respondTokenPair(
                                ctx, delegate, refreshToken, delegate.getDelegateKind().getAudience())))
                .mapEmpty();
    }

    /** Standard {@code [{id, name, provider}]} shape for "list of OIDC configs to choose from". */
    public void respondProvidersList(RoutingContext ctx, List<? extends OidcConfiguration> configs) {
        ctx.response().putHeader("Content-Type", "application/json").end(providersJson(configs).encode());
    }

    /** The {@code [{id, name, provider}]} array behind {@link #respondProvidersList}, for embedding in larger payloads. */
    public JsonArray providersJson(List<? extends OidcConfiguration> configs) {
        JsonArray arr = new JsonArray();
        for (OidcConfiguration c : configs) {
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
     * URL the IdP returns to, which is what distinguishes a signup start from a login start. The
     * flow names no organization: its callback resolves the identity across every organization.
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

        oidcConfigurationService.findEnabledOrgSignupByProvider(providerKind)
              .onSuccess(config -> {
                  if (config == null) {
                      respondError(ctx, 400, "Unknown or disabled platform provider: " + provider);
                  } else {
                      redirectToIdp(ctx, config, callbackUrl.apply(config.getId()), null, SessionBinding.origin(ctx));
                  }
              })
              .onFailure(ex -> {
                  log.error("Social start failed for provider {}", provider, ex);
                  respondError(ctx, 500, "Provider initialization failed");
              });
    }

    /**
     * Begins a sign-in through {@code config} and redirects the browser to its IdP, which returns to
     * {@code callbackUrl}. The completed login is bound to the page at {@code origin}, and the flow is
     * scoped to the organization {@code orgId}, or to none when it is {@code null}. A {@code referer}
     * query parameter naming a path within the SPA is where the browser lands once the flow completes.
     */
    public void redirectToIdp(RoutingContext ctx, OidcConfiguration config, String callbackUrl, String orgId, String origin) {
        String returnPath = safeReturnPath(ctx.request().getParam("referer"));
        oidcFlowOrchestrator.startFlow(ctx, config, callbackUrl, orgId, origin)
              .onSuccess(url -> {
                  // written after startFlow, which regenerates the session id. each start
                  // defines where its own flow lands, so a path a previous one abandoned at
                  // the IdP is replaced rather than inherited
                  if (returnPath != null) {
                      ctx.session().put(RETURN_PATH_SESSION_KEY, returnPath);
                  } else {
                      ctx.session().remove(RETURN_PATH_SESSION_KEY);
                  }
                  ctx.response().setStatusCode(302).putHeader("Location", url).end();
              })
              .onFailure(ex -> {
                  log.error("Sign-in start failed for configuration {}", config.getId(), ex);
                  respondError(ctx, 500, "Provider initialization failed");
              });
    }

    /**
     * Standard email/password login endpoint: parses the JSON body, validates fields, runs the supplied
     * authenticate function, and on success establishes the browser session for the participant it
     * resolves — otherwise writes a generic {@code 401}. The handler only needs to provide the
     * authenticate call, which resolves the credentials to the participant of its scope, or
     * {@code null} for any failure.
     */
    public void handlePasswordLogin(RoutingContext ctx,
                                    BiFunction<String, String, Future<Participant>> authenticate) {
        JsonObject body = readJsonBody(ctx);
        String email = body.getString("email");
        String password = body.getString("password");
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            respondError(ctx, 400, "email and password are required");
            return;
        }
        authenticate.apply(email, password)
              .onSuccess(participant -> {
                  if (participant == null) {
                      // Generic 401 — covers unknown email, wrong password, no password, disabled, not a member.
                      respondError(ctx, 401, "Invalid credentials");
                      return;
                  }
                  respondSuccess(ctx, participant);
              })
              .onFailure(err -> {
                  log.warn("Password login error: {}", err.getMessage());
                  respondError(ctx, 401, "Invalid credentials");
              });
    }

    /**
     * "After the IdP returned" composite flow for an application's login callback: validates {@code sub} +
     * {@code email_verified}, looks up the application's user bound to the identity via the supplied
     * function, and signs it in to the application, redirecting success or error accordingly. A successful
     * login is bound to the page that started the flow. When the lookup finds no user, the configuration is
     * one an organization made for the scope and it creates users on sign-in, and {@code newUser} supplies
     * the unsaved user carrying the scope, the identity's verified email and name become that user, bound
     * to the identity, and the user is signed in. An email the scope already holds under another method is
     * refused with {@link OidcErrorCodes#ACCOUNT_EXISTS}; a scope that supplies no user refuses the
     * identity with {@link OidcErrorCodes#NO_ACCOUNT} as a plain login does. An account, which signs in
     * through a membership, is signed in by {@link #completeLogin} instead.
     *
     * @param userLookup takes the OIDC {@code sub} claim and returns the application's user, or null
     * @param newUser    the unsaved user an unknown identity becomes, carrying only its scope, or {@code null}
     *                   when the scope admits no new user; consulted only when a user would be created
     */
    public void completeOidcLogin(RoutingContext ctx,
                                  CallbackResult<? extends OidcConfiguration> result,
                                  Function<String, Future<UserParticipantIdentity>> userLookup,
                                  Supplier<Future<UserParticipantIdentity>> newUser) {
        String sub = verifiedSubject(ctx, result);
        if (sub != null) {
            Future<UserParticipantIdentity> user = userLookup.apply(sub).compose(found -> {
                Future<UserParticipantIdentity> ret;
                if (found == null && result.config() instanceof OwnedOidcConfiguration owned && owned.isCreateUserOnSignIn()) {
                    ret = newUser.get().compose(template -> template == null
                            ? Future.succeededFuture()
                            : createUserFromClaims(template, sub, result));
                } else {
                    ret = Future.succeededFuture(found);
                }
                return ret;
            });
            completeLogin(ctx, result.origin(), user.map(found -> found == null ? null : participantOf(found)));
        }
    }

    /**
     * The participant an application's user signs in as, itself; a disabled user fails the sign-in with
     * {@link OidcErrorCodes#ACCOUNT_DISABLED}.
     */
    public static Participant participantOf(UserParticipantIdentity user) {
        if (!user.isEnabled()) {
            throw new OidcCallbackException(OidcErrorCodes.ACCOUNT_DISABLED);
        }
        return DomainUtil.createParticipant(user);
    }

    /**
     * Creates the user an identity the provider verified becomes on its first sign-in: {@code template},
     * carrying only its scope, given the identity's verified email and name and bound to the identity. A
     * scope that already holds the email under another method refuses the identity with
     * {@link OidcErrorCodes#ACCOUNT_EXISTS}, and an identity whose provider asserts no email with
     * {@link OidcErrorCodes#INVALID_TOKEN}.
     */
    public Future<UserParticipantIdentity> createUserFromClaims(UserParticipantIdentity template,
                                                                String sub,
                                                                CallbackResult<? extends OidcConfiguration> result) {
        Map<String, Object> claims = result.claims();
        String email = OAuth2Util.stringClaim(claims, "email");
        Future<UserParticipantIdentity> ret;
        if (email == null) {
            ret = Future.failedFuture(new OidcCallbackException(OidcErrorCodes.INVALID_TOKEN));
        } else {
            template.setEmail(email)
                    .setDisplayName(OAuth2Util.firstPresent(claims, "name", "preferred_username", "email"));
            ret = identityService.createOidcUser(template, result.config().getId(), sub)
                                 .recover(err -> Future.failedFuture(err instanceof IllegalArgumentException
                                         ? new OidcCallbackException(OidcErrorCodes.ACCOUNT_EXISTS) : err));
        }
        return ret;
    }

    /**
     * The {@code sub} claim of an identity the IdP returned that may sign in: one with a {@code sub}
     * and, for a provider that asserts it, a verified email. Otherwise redirects the browser to the
     * error page of the page that started the flow and returns {@code null}.
     */
    public String verifiedSubject(RoutingContext ctx, CallbackResult<? extends OidcConfiguration> result) {
        Map<String, Object> claims = result.claims();
        String sub = OAuth2Util.stringClaim(claims, "sub");
        String ret;
        if (sub == null) {
            redirectError(ctx, result.origin(), OidcErrorCodes.INVALID_TOKEN);
            ret = null;
        } else if (!OAuth2Util.isEmailVerified(claims, result.config().getProvider())) {
            redirectError(ctx, result.origin(), OidcErrorCodes.EMAIL_NOT_VERIFIED);
            ret = null;
        } else {
            ret = sub;
        }
        return ret;
    }

    /**
     * Finishes a sign-in from the participant a verified identity resolves to: the participant is signed
     * in, bound to the page at {@code origin}, and the browser sent back to it; no participant sends the
     * browser to that page's error page with {@link OidcErrorCodes#NO_ACCOUNT}, and a failed resolution
     * with the {@link OidcCallbackException}'s code, or {@link OidcErrorCodes#LOOKUP_FAILED} for any
     * other failure.
     *
     * @param origin      the page the sign-in belongs to, or {@code null} for this server's UI
     * @param participant the participant the identity signs in as, or {@code null} when there is none
     */
    public void completeLogin(RoutingContext ctx, String origin, Future<Participant> participant) {
        participant.onSuccess(found -> {
                  if (found == null) {
                      redirectError(ctx, origin, OidcErrorCodes.NO_ACCOUNT);
                  } else {
                      redirectSuccess(ctx, origin, found);
                  }
              })
              .onFailure(err -> {
                  String code;
                  if (err instanceof OidcCallbackException oce) {
                      code = oce.getErrorCode();
                  } else {
                      log.warn("Login resolution failed: {}", err.getMessage());
                      code = OidcErrorCodes.LOOKUP_FAILED;
                  }
                  redirectError(ctx, origin, code);
              });
    }
}
