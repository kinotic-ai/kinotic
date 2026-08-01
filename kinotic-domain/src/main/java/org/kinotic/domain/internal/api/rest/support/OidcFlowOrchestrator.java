package org.kinotic.domain.internal.api.rest.support;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.*;
import io.vertx.ext.auth.oauth2.providers.GithubAuth;
import io.vertx.ext.auth.oauth2.providers.OpenIDConnectAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.secret.SecretReferenceResolver;
import org.kinotic.domain.api.model.iam.BaseOidcConfiguration;
import org.kinotic.domain.api.model.iam.OidcProviderKind;
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
 * own per-route config resolver — the orchestrator itself knows nothing about IamUser
 * provisioning, JWT minting, or which entity table the configuration came from.
 *
 * <p>GitHub ({@link OidcProviderKind#GITHUB}) runs the same flow as a plain OAuth2
 * provider: it publishes no OIDC discovery document and issues no id_token, so its
 * identity claims are read from the GitHub REST API into the standard claim names
 * ({@code sub}, {@code email}, {@code name}) — callers see one claims shape either way.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OidcFlowOrchestrator {

    private static final String OIDC_FLOW_SESSION_KEY = "oidcFlow";
    private static final List<String> GITHUB_SCOPES = List.of("read:user", "user:email");
    private static final String GITHUB_EMAILS_URL = "https://api.github.com/user/emails";
    private static final String GITHUB_ACCEPT = "application/vnd.github+json";
    private static final String GITHUB_USER_AGENT = "kinotic-platform";

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
    void start() {
        webClient = WebClient.create(vertx);
    }

    /**
     * Validates the callback (state match, no IdP error), exchanges the code, validates the
     * identity, and returns the configuration along with the verified identity claims —
     * id_token claims for OIDC providers, REST-derived claims under the same names for GitHub.
     * The handler decides what to do with the claims (look up an IamUser, create a
     * {@code PendingSignUp}, etc.).
     *
     * <p>The session is consumed regardless of outcome — replay protection.
     *
     * @param configResolver loads the config for this route, scoped by the {@code orgId}
     *                       recovered from the flow session (or {@code null} when the flow
     *                       stashed none — non-org-scoped routes ignore the argument). Must
     *                       return a {@link CompletableFuture} resolving to {@code null} when
     *                       the config is unknown; a config that is no longer enabled is
     *                       rejected here, so the resolver need not check it.
     */
    public <C extends BaseOidcConfiguration> Future<CallbackResult<C>> handleCallback(
            RoutingContext ctx,
            String pathConfigId,
            String callbackUrl,
            Function<String, CompletableFuture<C>> configResolver) {

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

        return Future.fromCompletionStage(configResolver.apply(flowSession.orgId()))
                     .compose(config -> {
                         // Re-checked here rather than in each resolver: a config disabled while the
                         // user was at the IdP must not complete the flow it started.
                         if (config == null || !config.isEnabled()) {
                             return Future.failedFuture(new OidcCallbackException(OidcErrorCodes.CONFIG_NOT_FOUND));
                         }
                         return getOAuth2Auth(config)
                                 .compose(oauth2 -> exchangeCode(oauth2, code, callbackUrl, flowSession.pkceVerifier())
                                         .compose(user -> config.getProvider() == OidcProviderKind.GITHUB
                                                 ? githubClaims(oauth2, user)
                                                 : Future.succeededFuture(validatedIdTokenClaims(config, flowSession, user))))
                                 .map(claims -> new CallbackResult<>(config, claims, flowSession.orgId(), flowSession.inviteToken()));
                     });
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
        String state = OAuth2Util.randomUrlSafe(32);
        Session session = ctx.session();
        session.regenerateId();

        Future<String> ret;
        if (config.getProvider() == OidcProviderKind.GITHUB) {
            // GitHub issues no id_token (nothing to echo a nonce in) and does not support PKCE —
            // the state check plus the confidential client secret protect the code exchange.
            session.put(OIDC_FLOW_SESSION_KEY, new OidcFlowSession(state, null, null, config.getId(), orgId, inviteToken));
            ret = getOAuth2Auth(config)
                    .map(oauth2 -> oauth2.authorizeURL(
                            new OAuth2AuthorizationURL()
                                    .setRedirectUri(callbackUrl)
                                    .setScopes(GITHUB_SCOPES)
                                    .setState(state)));
        } else {
            String nonce = OAuth2Util.randomUrlSafe(32);
            String pkceVerifier = OAuth2Util.randomUrlSafe(64);
            String pkceChallenge = OAuth2Util.s256Challenge(pkceVerifier);
            session.put(OIDC_FLOW_SESSION_KEY, new OidcFlowSession(state, nonce, pkceVerifier, config.getId(), orgId, inviteToken));
            ret = getOAuth2Auth(config)
                    .map(oauth2 -> oauth2.authorizeURL(
                            new OAuth2AuthorizationURL()
                                    .setRedirectUri(callbackUrl)
                                    .setScopes(List.of("openid", "email", "profile"))
                                    .setState(state)
                                    .setCodeChallenge(pkceChallenge)
                                    .setCodeChallengeMethod("S256")
                                    .putAdditionalParameter("nonce", nonce)));
        }
        return ret;
    }

    /**
     * @param config       the persisted OIDC configuration (must have authority set, except for
     *                     GitHub whose endpoints are fixed)
     * @param clientSecret resolved client secret, or null for public-client flows
     */
    private Future<OAuth2Auth> createOAuth2Auth(BaseOidcConfiguration config, String clientSecret) {
        Future<OAuth2Auth> ret;
        if (config.getProvider() == OidcProviderKind.GITHUB) {
            if (clientSecret == null || clientSecret.isBlank()) {
                // Fail here rather than at the token endpoint: GitHub has no PKCE public-client
                // fallback, so a missing secret can never complete an exchange.
                ret = Future.failedFuture(new IllegalArgumentException(
                        "OidcConfiguration " + config.getId() + " resolved no client secret; required for GitHub"));
            } else {
                ret = Future.succeededFuture(GithubAuth.create(vertx, config.getClientId(), clientSecret));
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
     * The flattened id_token claims of {@code user}, validated against the flow: issuer,
     * audience, and nonce. Throws {@link OidcCallbackException} on any failure, which the
     * enclosing {@code compose} surfaces as a failed callback.
     */
    private Map<String, Object> validatedIdTokenClaims(BaseOidcConfiguration config, OidcFlowSession flowSession, User user) {
        Map<String, Object> claims = flattenClaims(user);
        if (!OAuth2Util.isIssuerValid(claims, config.getAuthority())) {
            log.warn("OIDC issuer validation failed for config {}: iss={}, tid={}",
                     config.getId(), claims.get("iss"), claims.get("tid"));
            throw new OidcCallbackException(OidcErrorCodes.INVALID_TOKEN);
        }
        if (!OAuth2Util.isAudienceValid(claims, config.getAudience())) {
            log.warn("OIDC audience validation failed for config {}: expected={}, aud={}",
                     config.getId(), config.getAudience(), claims.get("aud"));
            throw new OidcCallbackException(OidcErrorCodes.INVALID_TOKEN);
        }
        // startFlow always sends a nonce, so OIDC Core 3.1.3.7 requires the
        // id_token to echo it — an absent claim fails the equals and is rejected
        if (!flowSession.nonce().equals(OAuth2Util.stringClaim(claims, "nonce"))) {
            log.warn("OIDC nonce validation failed for config {}", config.getId());
            throw new OidcCallbackException(OidcErrorCodes.INVALID_TOKEN);
        }
        return claims;
    }

    /**
     * Builds the standard claims map for a GitHub identity from the REST API: {@code sub} is
     * GitHub's immutable numeric account id, {@code name}/{@code preferred_username} come from
     * the profile, and {@code email}/{@code email_verified} are set only when GitHub reports a
     * verified primary email — so the caller's {@link OAuth2Util#isEmailVerified} check fails
     * closed on unverified or inaccessible emails.
     */
    private Future<Map<String, Object>> githubClaims(OAuth2Auth oauth2, User user) {
        String accessToken = user.principal().getString("access_token");
        return oauth2.userInfo(user)
                     .compose(profile -> githubPrimaryEmail(accessToken).map(email -> {
                         Map<String, Object> claims = new HashMap<>();
                         // Guard against String.valueOf(null) — a literal "null" sub would collide
                         // every malformed profile on one identity key instead of being rejected.
                         Object id = profile.getValue("id");
                         if (id != null) {
                             claims.put("sub", String.valueOf(id));
                         }
                         String name = profile.getString("name");
                         if (name != null && !name.isBlank()) {
                             claims.put("name", name);
                         }
                         claims.put("preferred_username", profile.getString("login"));
                         if (email != null) {
                             claims.put("email", email);
                             claims.put("email_verified", true);
                         }
                         return claims;
                     }));
    }

    /**
     * The user's primary email per GitHub's emails API, or {@code null} when it is unverified
     * or the token cannot read emails. The emails endpoint is used because the profile's
     * {@code email} field only carries an email the user chose to show publicly.
     */
    private Future<String> githubPrimaryEmail(String accessToken) {
        return webClient.getAbs(GITHUB_EMAILS_URL)
                        .putHeader("Authorization", "Bearer " + accessToken)
                        .putHeader("Accept", GITHUB_ACCEPT)
                        .putHeader("User-Agent", GITHUB_USER_AGENT)
                        .send()
                        .map(resp -> {
                            String ret = null;
                            if (resp.statusCode() == 200) {
                                JsonArray emails = resp.bodyAsJsonArray();
                                for (int i = 0; i < emails.size(); i++) {
                                    JsonObject entry = emails.getJsonObject(i);
                                    if (Boolean.TRUE.equals(entry.getBoolean("primary"))
                                            && Boolean.TRUE.equals(entry.getBoolean("verified"))) {
                                        ret = entry.getString("email");
                                        break;
                                    }
                                }
                            } else {
                                log.warn("GitHub emails API answered {}: {}", resp.statusCode(), resp.bodyAsString());
                            }
                            return ret;
                        });
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
     * The {@link OAuth2Auth} for {@code config} as it is currently persisted, built on first use
     * (OIDC discovery against its authority, or the fixed-endpoint factory for GitHub) and cached
     * until an hour passes with no flow using it. Saving the configuration takes effect on the next
     * flow. A build that fails is not cached, so the next flow retries it.
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
                                                                             .thenCompose(secret -> createOAuth2Auth(config, secret)
                                                                                     .toCompletionStage()));
        return Future.fromCompletionStage(cached, vertx.getOrCreateContext())
                     .onFailure(err -> log.error("Failed to initialize OAuth2Auth for config {}", config.getId(), err));
    }

}
