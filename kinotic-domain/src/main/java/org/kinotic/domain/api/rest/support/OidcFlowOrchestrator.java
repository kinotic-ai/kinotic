package org.kinotic.domain.api.rest.support;

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
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.secret.SecretReferenceResolver;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SessionBinding;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OwnedOidcConfiguration;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.security.ManagedOidcConfiguration;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;
import org.kinotic.domain.internal.api.rest.support.OidcFlowSession;

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
    private final OidcConfigurationService oidcConfigurationService;
    private final Vertx vertx;
    private WebClient webClient;

    @PostConstruct
    public void start() {
        // an identity endpoint answers with the profile itself; a redirect is not followed to wherever it points
        this.webClient = WebClient.create(vertx, new WebClientOptions().setFollowRedirects(false));
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
     * @param configResolver loads the config for this route, scoped by the organization, application and
     *                       tenant recovered from the flow session (each {@code null} when the flow stashed
     *                       none — routes scoped otherwise ignore them). Must resolve to {@code null} when
     *                       the config is unknown; a config that is no longer enabled is rejected here, so
     *                       the resolver need not check it.
     */
    public <C extends OidcConfiguration> Future<CallbackResult<C>> handleCallback(
            RoutingContext ctx,
            String pathConfigId,
            String callbackUrl,
            FlowConfigResolver<C> configResolver) {

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

        return configResolver.resolve(flowSession.orgId(), flowSession.applicationId(), flowSession.tenantId())
                     .compose(config -> {
                         // Re-checked here rather than in each resolver: a config disabled while the
                         // user was at the IdP must not complete the flow it started.
                         if (config == null || !config.isEnabled()) {
                             return Future.failedFuture(new OidcCallbackException(OidcErrorCodes.CONFIG_NOT_FOUND));
                         }
                         return getOAuth2Auth(config)
                                 .compose(oauth2 -> exchangeCode(oauth2, code, callbackUrl, flowSession.pkceVerifier()))
                                 .compose(user -> verifiedClaims(config, user, flowSession))
                                 .map(claims -> new CallbackResult<>(config, claims, flowSession.purpose(), flowSession.orgId(),
                                                                   flowSession.applicationId(), flowSession.tenantId(),
                                                                   flowSession.inviteToken(), flowSession.accountId(),
                                                                   flowSession.origin()));
                     });
    }

    /**
     * Produces the verified identity claims for an exchanged token: id_token claims validated
     * against issuer, audience and nonce for OIDC providers, or claims fetched from the
     * provider's identity endpoint for a managed registration that sets {@code userInfoUri}.
     */
    private Future<Map<String, Object>> verifiedClaims(OidcConfiguration config, User user, OidcFlowSession flowSession) {
        Future<Map<String, Object>> ret;
        if (config instanceof ManagedOidcConfiguration social && social.getUserInfoUri() != null && !social.getUserInfoUri().isBlank()) {
            // A provider configured with an identity endpoint issues no id_token, so there is no
            // issuer/audience/nonce to check — the state match in handleCallback plus the direct
            // TLS code exchange are the security boundary, and identity comes from the provider's
            // API using the exchanged access token.
            ret = fetchApiClaims(social, user);
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
     * The flow the session holds for the callback about to be handled, or {@code null} when it holds none, for
     * a handler to route a failed callback by; {@link #handleCallback} consumes the flow itself.
     */
    public PendingFlow pendingFlow(RoutingContext ctx) {
        OidcFlowSession flowSession = ctx.session().get(OIDC_FLOW_SESSION_KEY);
        return flowSession == null ? null : new PendingFlow(flowSession.purpose(), flowSession.origin(), flowSession.inviteToken());
    }

    /**
     * Begins a {@link OidcFlowPurpose#LOGIN} flow signing in to the organization {@code orgId}, or to none when
     * it is {@code null}: generates state/nonce/PKCE, stores them on the session along with the page at
     * {@code origin} the completed login is bound to, builds the IdP authorization URL using the supplied
     * callback URL, and returns the URL. The caller names the origin because a start reached by a link
     * carries no {@code Origin} of its own.
     */
    public Future<String> startFlow(RoutingContext ctx,
                                    OidcConfiguration config,
                                    String callbackUrl,
                                    String orgId,
                                    String origin) {
        return startFlow(ctx, config, callbackUrl, OidcFlowPurpose.LOGIN, orgId, null, null, null, null, origin);
    }

    /**
     * {@link #startFlow(RoutingContext, OidcConfiguration, String, String, String)} for a sign-in to
     * {@code application}, or to its tenant {@code tenantId} when one is named: stashes the ids of the scope
     * for the callback to resolve its configuration by.
     */
    public Future<String> startFlow(RoutingContext ctx,
                                    OidcConfiguration config,
                                    String callbackUrl,
                                    ApplicationKey application,
                                    String tenantId,
                                    String origin) {
        return startFlow(ctx, config, callbackUrl, OidcFlowPurpose.LOGIN, application.organizationId(), application.applicationId(),
                         tenantId, null, null, origin);
    }

    /** Begins a {@link OidcFlowPurpose#SIGN_UP} flow through one of the platform's org sign-up registrations. */
    public Future<String> startSignUp(RoutingContext ctx, OidcConfiguration config, String callbackUrl) {
        return startFlow(ctx, config, callbackUrl, OidcFlowPurpose.SIGN_UP, null, null, null, null, null, SessionBinding.origin(ctx));
    }

    /**
     * Begins an {@link OidcFlowPurpose#INVITATION} flow accepting the invitation {@code inviteToken} into
     * organization {@code orgId}, stashed for the callback to resolve its configuration by and to complete the
     * acceptance with.
     */
    public Future<String> startInvitation(RoutingContext ctx, OidcConfiguration config, String callbackUrl, String orgId, String inviteToken) {
        Validate.notBlank(inviteToken, "inviteToken cannot be blank");
        return startFlow(ctx, config, callbackUrl, OidcFlowPurpose.INVITATION, orgId, null, null, inviteToken, null, SessionBinding.origin(ctx));
    }

    /**
     * Begins a {@link OidcFlowPurpose#CONNECT} flow binding the identity the provider asserts to account
     * {@code accountId}; {@code orgId} is the organization whose configuration the flow runs through, or
     * {@code null} for one of the platform's registrations.
     */
    public Future<String> startConnect(RoutingContext ctx, OidcConfiguration config, String callbackUrl, String orgId, String accountId) {
        Validate.notBlank(accountId, "accountId cannot be blank");
        return startFlow(ctx, config, callbackUrl, OidcFlowPurpose.CONNECT, orgId, null, null, null, accountId, SessionBinding.origin(ctx));
    }

    private Future<String> startFlow(RoutingContext ctx,
                                     OidcConfiguration config,
                                     String callbackUrl,
                                     OidcFlowPurpose purpose,
                                     String orgId,
                                     String applicationId,
                                     String tenantId,
                                     String inviteToken,
                                     String accountId,
                                     String origin) {
        String state = DomainUtil.generateUrlSafeToken(32);
        String nonce = DomainUtil.generateUrlSafeToken(32);
        String pkceVerifier = DomainUtil.generateUrlSafeToken(64);
        String pkceChallenge = OAuth2Util.s256Challenge(pkceVerifier);

        Session session = ctx.session();
        session.regenerateId();
        session.put(OIDC_FLOW_SESSION_KEY, new OidcFlowSession(state, nonce, pkceVerifier, config.getId(), purpose, orgId,
                                                               applicationId, tenantId, inviteToken, accountId, origin));

        // Scopes ride as RFC 6749's space-delimited string; a managed registration may name its own, every
        // other configuration gets the standard OIDC triple.
        List<String> scopes = config instanceof ManagedOidcConfiguration social
                              && social.getScopes() != null && !social.getScopes().isBlank()
                ? List.of(social.getScopes().split(" "))
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
    private Future<OAuth2Auth> createOAuth2Auth(OidcConfiguration config, String clientSecret) {
        Future<OAuth2Auth> ret;
        // only a managed registration may name endpoints in place of discovery
        ManagedOidcConfiguration explicit = config instanceof ManagedOidcConfiguration social
                                              && (social.getAuthorizationUri() != null || social.getTokenUri() != null)
                ? social : null;
        if (explicit != null) {
            if (explicit.getAuthorizationUri() == null || explicit.getTokenUri() == null) {
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
                        .setAuthorizationPath(explicit.getAuthorizationUri())
                        .setTokenPath(explicit.getTokenUri())
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
    private Future<Map<String, Object>> fetchApiClaims(ManagedOidcConfiguration config, User user) {
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
    private Future<OAuth2Auth> getOAuth2Auth(OidcConfiguration config) {
        // Keying on updated as well as id is what picks up an edited clientId/authority or a replaced
        // client secret, which is saved along with the row: both call sites pass a row read for this
        // request, so a save misses the cache on every node without an invalidation message, and the
        // superseded entry ages out on its own.
        String key = config.getId() + ':' + (config.getUpdated() != null ? config.getUpdated().getTime() : 0);
        // AsyncCache evicts an entry whose future completes exceptionally, which is what keeps a
        // transient discovery failure from being cached — the loader must not touch the cache itself
        CompletableFuture<OAuth2Auth> cached =
                oauth2AuthCache.get(key,
                                    (id, executor) -> clientSecretOf(config)
                                            .compose(secret -> createOAuth2Auth(config, secret))
                                            .toCompletionStage()
                                            .toCompletableFuture());
        return Future.fromCompletionStage(cached, vertx.getOrCreateContext())
                     .onFailure(err -> log.error("Failed to initialize OAuth2Auth for config {}", config.getId(), err));
    }

    /**
     * The client secret {@code config} sends its provider, or null for a public client, read from where
     * its kind of configuration keeps it: the platform's secret storage, under the name the operator
     * gave a managed registration, or the owning organization's scope of the secret storage for a
     * configuration an organization saved.
     */
    private Future<String> clientSecretOf(OidcConfiguration config) {
        return switch (config) {
            case ManagedOidcConfiguration managed -> secretReferenceResolver.resolve(managed.getSecretNameRef());
            // an organization's administrator controls this row, so nothing on it may name a platform secret
            case OwnedOidcConfiguration owned -> oidcConfigurationService.findClientSecret(owned);
        };
    }

}
