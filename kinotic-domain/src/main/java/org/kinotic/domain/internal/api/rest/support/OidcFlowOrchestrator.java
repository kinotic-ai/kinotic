package org.kinotic.domain.internal.api.rest.support;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.*;
import io.vertx.ext.auth.oauth2.providers.OpenIDConnectAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.secret.SecretReferenceResolver;
import org.kinotic.domain.api.model.security.BaseOidcConfiguration;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.rest.OidcErrorCodes;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Owns the OAuth 2.0 / OIDC flow shared by every handler that bounces the user out to
 * an IdP and back: state/nonce/PKCE generation, session storage, callback validation,
 * code exchange, claim extraction, issuer validation. Handlers compose it with their
 * own per-route config resolver — the orchestrator itself knows nothing about ParticipantIdentity
 * provisioning, JWT minting, or which entity table the configuration came from.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OidcFlowOrchestrator {

    private static final String OIDC_FLOW_SESSION_KEY = "oidcFlow";
    private static final String USER_AGENT = "kinotic-platform";

    private final AsyncCache<String, OAuth2Auth> oauth2AuthCache =
            Caffeine.newBuilder()
                    // OpenIDConnectAuth.discover leaves each provider holding a periodic JWKS refresh timer;
                    // close() cancels it, so an entry dropped by either policy does not leak one
                    .removalListener((String id, OAuth2Auth oauth2Auth, RemovalCause cause) -> oauth2Auth.close())
                    .expireAfterAccess(Duration.ofHours(1))
                    .maximumSize(1_000)
                    .buildAsync();
    private final SecretReferenceResolver secretReferenceResolver;
    private final Vertx vertx;
    private WebClient webClient;

    @PostConstruct
    public void start() {
        this.webClient = WebClient.create(vertx);
    }

    @PreDestroy
    public void stop() {
        if (webClient != null) {
            webClient.close();
        }
    }

    /**
     * Validates the callback (state match, no IdP error), exchanges the code, and returns the
     * configuration along with the verified identity claims ({@code sub}, {@code email},
     * {@code email_verified}, {@code name}, …).
     * The handler decides what to do with the claims (look up a ParticipantIdentity, create a
     * {@code PendingSignUp}, etc.).
     *
     * <p>The session is consumed regardless of outcome — replay protection.
     *
     * @param configResolver loads the config for this route, scoped by the {@code orgId}
     *                       recovered from the flow session (or {@code null} when the flow
     *                       stashed none — non-org-scoped routes ignore the argument). Must
     *                       return a {@link Future} resolving to {@code null} when
     *                       the config is unknown; a config that is no longer enabled is
     *                       rejected here, so the resolver need not check it.
     */
    public <C extends BaseOidcConfiguration> Future<CallbackResult<C>> handleCallback(
            RoutingContext ctx,
            String pathConfigId,
            String callbackUrl,
            Function<String, Future<C>> configResolver) {

        String code = ctx.request().getParam("code");
        String state = ctx.request().getParam("state");
        String idpError = ctx.request().getParam("error");

        if (idpError != null) {
            log.info("OIDC callback error from config {}: {}", pathConfigId, idpError);
            return Future.failedFuture(new OidcCallbackException(idpError));
        }
        if (code == null || state == null) {
            return Future.failedFuture(new OidcCallbackException(OidcErrorCodes.INVALID_CALLBACK));
        }

        Session session = ctx.session();
        OidcFlowSession flowSession = session.remove(OIDC_FLOW_SESSION_KEY);

        if (flowSession == null || !flowSession.state().equals(state)
                || !flowSession.configId().equals(pathConfigId)) {
            log.warn("OIDC callback state mismatch for configId={}", pathConfigId);
            return Future.failedFuture(new OidcCallbackException(OidcErrorCodes.STATE_MISMATCH));
        }

        return configResolver.apply(flowSession.orgId())
                     .compose(config -> {
                         // Re-checked here rather than in each resolver: a config disabled while the
                         // user was at the IdP must not complete the flow it started.
                         if (config == null || !config.isEnabled()) {
                             return Future.failedFuture(new OidcCallbackException(OidcErrorCodes.CONFIG_NOT_FOUND));
                         }
                         return getOAuth2Auth(config)
                                 .compose(oauth2 -> exchangeCode(oauth2, code, callbackUrl, flowSession.pkceVerifier()))
                                 .compose(user -> verifiedClaims(config, user, flowSession))
                                 .map(claims -> new CallbackResult<>(config, claims, flowSession.orgId(), flowSession.inviteToken()));
                     });
    }

    /**
     * Produces the verified identity claims for an exchanged token: id_token claims validated
     * against issuer, audience and nonce for OIDC providers, or claims fetched from the
     * provider's identity endpoint for rows that set {@code userInfoUri}.
     */
    private Future<Map<String, Object>> verifiedClaims(BaseOidcConfiguration config, User user, OidcFlowSession flowSession) {
        Future<Map<String, Object>> ret;
        if (config.getUserInfoUri() != null && !config.getUserInfoUri().isBlank()) {
            // A provider configured with an identity endpoint issues no id_token, so there is no
            // issuer/audience/nonce to check — the state match in handleCallback plus the direct
            // TLS code exchange are the security boundary, and identity comes from the provider's
            // API using the exchanged access token.
            ret = fetchApiClaims(config, user);
        } else {
            Map<String, Object> claims = flattenClaims(user);
            if (!OAuth2Util.isIssuerValid(claims, config.getAuthority())) {
                log.warn("OIDC issuer validation failed for config {}: iss={}, tid={}",
                         config.getId(), claims.get("iss"), claims.get("tid"));
                ret = Future.failedFuture(new OidcCallbackException(OidcErrorCodes.INVALID_TOKEN));
            } else if (!OAuth2Util.isAudienceValid(claims, config.getAudience())) {
                log.warn("OIDC audience validation failed for config {}: expected={}, aud={}",
                         config.getId(), config.getAudience(), claims.get("aud"));
                ret = Future.failedFuture(new OidcCallbackException(OidcErrorCodes.INVALID_TOKEN));
            } else if (!flowSession.nonce().equals(OAuth2Util.stringClaim(claims, "nonce"))) {
                // startFlow always sends a nonce, so OIDC Core 3.1.3.7 requires the
                // id_token to echo it — an absent claim fails the equals and is rejected
                log.warn("OIDC nonce validation failed for config {}", config.getId());
                ret = Future.failedFuture(new OidcCallbackException(OidcErrorCodes.INVALID_TOKEN));
            } else {
                ret = Future.succeededFuture(claims);
            }
        }
        return ret;
    }

    /**
     * Generates state/nonce/PKCE, stores them on the session, builds the IdP authorization
     * URL using the supplied callback URL, and returns the URL.
     *
     * @param orgId the organization id to stash on the session for the callback to scope its
     *              config lookup by, or {@code null} for non-org-scoped flows.
     */
    public Future<String> startFlow(RoutingContext ctx,
                                    BaseOidcConfiguration config,
                                    String callbackUrl,
                                    String orgId) {
        return startFlow(ctx, config, callbackUrl, orgId, null);
    }

    /**
     * {@link #startFlow(RoutingContext, BaseOidcConfiguration, String, String)} for an
     * invitation-accept flow: additionally stashes the invite's accept token on the session
     * so the callback can complete the acceptance.
     */
    public Future<String> startFlow(RoutingContext ctx,
                                    BaseOidcConfiguration config,
                                    String callbackUrl,
                                    String orgId,
                                    String inviteToken) {
        String state = DomainUtil.generateUrlSafeToken(32);
        String nonce = DomainUtil.generateUrlSafeToken(32);
        String pkceVerifier = DomainUtil.generateUrlSafeToken(64);
        String pkceChallenge = OAuth2Util.s256Challenge(pkceVerifier);

        Session session = ctx.session();
        session.regenerateId();
        session.put(OIDC_FLOW_SESSION_KEY, new OidcFlowSession(state, nonce, pkceVerifier, config.getId(), orgId, inviteToken));

        // Scopes ride as RFC 6749's space-delimited string; a row without them gets the
        // standard OIDC triple.
        List<String> scopes = config.getScopes() != null && !config.getScopes().isBlank()
                ? List.of(config.getScopes().split(" "))
                : List.of("openid", "email", "profile");

        return getOAuth2Auth(config)
                .map(oauth2 -> oauth2.authorizeURL(
                        new OAuth2AuthorizationURL()
                                .setRedirectUri(callbackUrl)
                                .setScopes(scopes)
                                .setState(state)
                                .setCodeChallenge(pkceChallenge)
                                .setCodeChallengeMethod("S256")
                                .putAdditionalParameter("nonce", nonce)));
    }

    /**
     * @param config       the persisted OIDC configuration (must have authority set)
     * @param clientSecret resolved client secret, or null for public-client flows
     */
    private Future<OAuth2Auth> createOAuth2Auth(BaseOidcConfiguration config, String clientSecret) {
        Future<OAuth2Auth> ret;
        if (config.getAuthorizationUri() != null || config.getTokenUri() != null) {
            if (config.getAuthorizationUri() == null || config.getTokenUri() == null) {
                ret = Future.failedFuture(new IllegalArgumentException(
                        "OidcConfiguration " + config.getId()
                                + " must set both authorizationUri and tokenUri to bypass OIDC discovery"));
            } else {
                // Explicit endpoints — the provider publishes no OIDC discovery document.
                // Vert.x uses an absolute URL in a *Path option as-is; OAuth2API prepends
                // the site only when the value starts with '/'.
                OAuth2Options options = new OAuth2Options()
                        .setClientId(config.getClientId())
                        .setSite(config.getAuthority())
                        .setAuthorizationPath(config.getAuthorizationUri())
                        .setTokenPath(config.getTokenUri())
                        // some providers (github.com) reject requests that carry no User-Agent
                        .setHeaders(new JsonObject().put("User-Agent", USER_AGENT));
                if (clientSecret != null) {
                    options.setClientSecret(clientSecret);
                }
                ret = Future.succeededFuture(OAuth2Auth.create(vertx, options));
            }
        } else if (config.getAuthority() == null || config.getAuthority().isBlank()) {
            ret = Future.failedFuture(new IllegalArgumentException(
                    "OidcConfiguration " + config.getId() + " has no authority; required for OIDC discovery"));
        } else {
            OAuth2Options options = new OAuth2Options()
                    .setClientId(config.getClientId())
                    .setSite(config.getAuthority())
                    // Microsoft /common returns a discovery doc whose `issuer` is the literal template
                    // "https://login.microsoftonline.com/{tenantid}/v2.0" — Vert.x's strict
                    // {site == issuer} comparison fails. JWKS-backed signature verification remains
                    // the real security check.
                    .setValidateIssuer(false);
            if (clientSecret != null) {
                options.setClientSecret(clientSecret);
            }

            ret = OpenIDConnectAuth.discover(vertx, options)
                                   .map(oauth -> {
                                       if (options.getJWTOptions() != null) {
                                           // Discovery mutates JWTOptions.issuer with the
                                           // {tenantid}-templated string — Vert.x would then fail every
                                           // per-JWT validation because the real JWT carries the
                                           // substituted tid. Clear it for the multi-tenant case; we
                                           // re-validate the issuer ourselves post-exchange using the
                                           // JWT's signed tid claim.
                                           String jwtIssuer = options.getJWTOptions().getIssuer();
                                           if (jwtIssuer != null && jwtIssuer.contains("{tenantid}")) {
                                               options.getJWTOptions().setIssuer(null);
                                           }
                                           // Belt-and-suspenders audience check: when configured, push
                                           // the expected audience into Vert.x's JWTOptions so the aud
                                           // claim is validated during token-exchange JWT processing.
                                           // The orchestrator re-validates post-exchange so coverage
                                           // holds across Vert.x version drift.
                                           if (config.getAudience() != null && !config.getAudience().isBlank()) {
                                               options.getJWTOptions().addAudience(config.getAudience());
                                           }
                                       }
                                       return oauth;
                                   });
        }
        return ret;
    }

    private Future<User> exchangeCode(OAuth2Auth oauth2, String code, String callbackUrl, String pkceVerifier) {
        return oauth2.authenticate(new Oauth2Credentials()
                                           .setFlow(OAuth2FlowType.AUTH_CODE)
                                           .setCode(code)
                                           .setRedirectUri(callbackUrl)
                                           .setCodeVerifier(pkceVerifier));
    }

    /**
     * Extracts OIDC claims from a Vert.x {@link User}. Vert.x v5 puts the decoded id_token
     * claims under {@code user.attributes().getJsonObject("idToken")} — that's where {@code
     * iss}, {@code tid}, {@code sub}, {@code email}, etc. live. The principal itself only
     * carries the raw token-endpoint response (encoded JWT strings).
     */
    private Map<String, Object> flattenClaims(User user) {
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

    /**
     * Builds the identity claims for a provider with no id_token by querying the endpoints
     * on its configuration: {@code userInfoUri} supplies {@code sub}, {@code name} and
     * {@code email}; {@code userEmailsUri}, when set, supplies the definitive {@code email}
     * and {@code email_verified} from a GitHub-style emails array.
     */
    private Future<Map<String, Object>> fetchApiClaims(BaseOidcConfiguration config, User user) {
        String accessToken = user.principal().getString("access_token");
        return apiGet(accessToken, config.getUserInfoUri())
                .map(response -> profileClaims(response.bodyAsJsonObject()))
                .compose(claims -> {
                    Future<Map<String, Object>> ret;
                    if (config.getUserEmailsUri() != null && !config.getUserEmailsUri().isBlank()) {
                        ret = apiGet(accessToken, config.getUserEmailsUri())
                                .map(response -> withBestEmail(claims, response.bodyAsJsonArray()));
                    } else {
                        ret = Future.succeededFuture(claims);
                    }
                    return ret;
                });
    }

    /**
     * Maps an identity-endpoint profile to claims, reading the standard OIDC userinfo names
     * first and the GitHub names as fallbacks: {@code sub} else numeric {@code id},
     * {@code name} else {@code login}, {@code email} / {@code email_verified} as given.
     */
    private static Map<String, Object> profileClaims(JsonObject profile) {
        Map<String, Object> claims = new HashMap<>();
        Object sub = profile.getValue("sub") != null ? profile.getValue("sub") : profile.getValue("id");
        if (sub != null) {
            claims.put("sub", String.valueOf(sub));
        }
        String name = profile.getString("name");
        if (name == null || name.isBlank()) {
            name = profile.getString("login");
        }
        if (name != null) {
            claims.put("name", name);
        }
        String email = profile.getString("email");
        if (email != null) {
            claims.put("email", email);
            Object verified = profile.getValue("email_verified");
            if (verified != null) {
                claims.put("email_verified", verified);
            }
        }
        return claims;
    }

    /** Replaces the email claims with the best address from a GitHub-style emails array. */
    private static Map<String, Object> withBestEmail(Map<String, Object> claims, JsonArray emails) {
        JsonObject best = bestEmail(emails);
        if (best != null) {
            claims.put("email", best.getString("email"));
            claims.put("email_verified", Boolean.TRUE.equals(best.getBoolean("verified")));
        }
        return claims;
    }

    /**
     * The primary verified entry, else any verified entry, else the primary entry;
     * {@code null} when the account has no address matching any of these.
     */
    private static JsonObject bestEmail(JsonArray emails) {
        JsonObject verified = null;
        JsonObject primary = null;
        for (int i = 0; i < emails.size(); i++) {
            JsonObject entry = emails.getJsonObject(i);
            boolean isPrimary = Boolean.TRUE.equals(entry.getBoolean("primary"));
            if (Boolean.TRUE.equals(entry.getBoolean("verified"))) {
                if (isPrimary) {
                    // Primary + verified wins outright
                    verified = entry;
                    break;
                }
                if (verified == null) {
                    verified = entry;
                }
            } else if (isPrimary) {
                primary = entry;
            }
        }
        // An unverified primary still surfaces so the flow fails as EMAIL_NOT_VERIFIED
        // rather than the opaque missing-claims INVALID_TOKEN
        return verified != null ? verified : primary;
    }

    private Future<HttpResponse<Buffer>> apiGet(String accessToken, String uri) {
        return webClient.getAbs(uri)
                        .putHeader("Authorization", "Bearer " + accessToken)
                        .putHeader("Accept", "application/json")
                        // some provider APIs (api.github.com) reject requests that carry no User-Agent
                        .putHeader("User-Agent", USER_AGENT)
                        .send()
                        .compose(response -> {
                            Future<HttpResponse<Buffer>> ret;
                            if (response.statusCode() == 200) {
                                ret = Future.succeededFuture(response);
                            } else {
                                // On GitHub, a 403 from /user/emails usually means the app lacks the
                                // "Email addresses: read-only" account permission
                                log.warn("Identity API GET {} failed with status {}", uri, response.statusCode());
                                ret = Future.failedFuture(new OidcCallbackException(OidcErrorCodes.EXCHANGE_FAILED));
                            }
                            return ret;
                        });
    }

    /**
     * The {@code OAuth2Auth} for {@code config} as it is currently persisted, discovered against its
     * authority on first use and cached until an hour passes with no flow using it. Saving the
     * configuration takes effect on the next flow. A discovery that fails is not cached, so the next
     * flow retries it.
     */
    private Future<OAuth2Auth> getOAuth2Auth(BaseOidcConfiguration config) {
        // Keying on updated as well as id is what picks up an edited clientId/authority/secretNameRef:
        // both call sites pass a row read for this request, so a save misses the cache on every node
        // without an invalidation message, and the superseded entry ages out on its own.
        String key = config.getId() + ':' + (config.getUpdated() != null ? config.getUpdated().getTime() : 0);
        // AsyncCache evicts an entry whose future completes exceptionally, which is what keeps a
        // transient discovery failure from being cached — the loader must not touch the cache itself
        CompletableFuture<OAuth2Auth> cached =
                oauth2AuthCache.get(key,
                                    (id, executor) -> secretReferenceResolver.resolve(config.getSecretNameRef())
                                                                             .compose(secret -> createOAuth2Auth(config, secret))
                                                                             .toCompletionStage()
                                                                             .toCompletableFuture());
        return Future.fromCompletionStage(cached, vertx.getOrCreateContext())
                     .onFailure(err -> log.error("Failed to initialize OAuth2Auth for config {}", config.getId(), err));
    }

}
