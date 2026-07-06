package org.kinotic.domain.internal.api.rest.support;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2AuthorizationURL;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.Oauth2Credentials;
import io.vertx.ext.auth.oauth2.providers.OpenIDConnectAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.secret.SecretReferenceResolver;
import org.kinotic.domain.internal.api.rest.OidcErrorCodes;
import org.kinotic.domain.api.model.iam.BaseOidcConfiguration;
import org.kinotic.domain.api.model.iam.OidcConfiguration;
import org.kinotic.domain.api.model.iam.OrgSignupOidcConfiguration;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Owns the OAuth 2.0 / OIDC flow shared by every handler that bounces the user out to
 * an IdP and back: state/nonce/PKCE generation, session storage, callback validation,
 * code exchange, claim extraction, issuer validation. Handlers compose it with their
 * own per-route config resolver — the orchestrator itself knows nothing about IamUser
 * provisioning, JWT minting, or which entity table the configuration came from.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OidcFlowOrchestrator {

    private static final String OIDC_FLOW_SESSION_KEY = "oidcFlow";

    private final ConcurrentMap<String, Future<OAuth2Auth>> oauth2AuthCache = new ConcurrentHashMap<>();
    private final SecretReferenceResolver secretReferenceResolver;
    private final Vertx vertx;

    /**
     * Validates the callback (state match, no IdP error), exchanges the code, validates
     * the issuer, and returns the configuration along with the verified id_token claims.
     * The handler decides what to do with the claims (look up an IamUser, create a
     * {@code PendingSignUp}, etc.).
     *
     * <p>The session is consumed regardless of outcome — replay protection.
     *
     * @param configResolver loads the config for this route, scoped by the {@code orgId}
     *                       recovered from the flow session (or {@code null} when the flow
     *                       stashed none — non-org-scoped routes ignore the argument). Must
     *                       return a {@link CompletableFuture} resolving to {@code null} when
     *                       the config is unknown.
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
                         if (config == null) {
                             return Future.failedFuture(new OidcCallbackException(OidcErrorCodes.CONFIG_NOT_FOUND));
                         }
                         return getOAuth2Auth(config)
                                 .compose(oauth2 -> exchangeCode(oauth2, code, callbackUrl, flowSession.pkceVerifier()))
                                 .map(user -> {
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
                                     return new CallbackResult<>(config, claims, flowSession.orgId(), flowSession.inviteToken());
                                 });
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
        String nonce = OAuth2Util.randomUrlSafe(32);
        String pkceVerifier = OAuth2Util.randomUrlSafe(64);
        String pkceChallenge = OAuth2Util.s256Challenge(pkceVerifier);

        Session session = ctx.session();
        session.regenerateId();
        session.put(OIDC_FLOW_SESSION_KEY, new OidcFlowSession(state, nonce, pkceVerifier, config.getId(), orgId, inviteToken));

        return getOAuth2Auth(config)
                .map(oauth2 -> oauth2.authorizeURL(
                        new OAuth2AuthorizationURL()
                                .setRedirectUri(callbackUrl)
                                .setScopes(List.of("openid", "email", "profile"))
                                .setState(state)
                                .setCodeChallenge(pkceChallenge)
                                .setCodeChallengeMethod("S256")
                                .putAdditionalParameter("nonce", nonce)));
    }

    private Future<OAuth2Auth> buildOAuth2Auth(BaseOidcConfiguration config) {
        return Future.fromCompletionStage(secretReferenceResolver.resolve(secretNameOf(config)))
                     .compose(secret -> createOAuth2Auth(config, secret))
                     .onFailure(err -> {
                         log.error("Failed to initialize OAuth2Auth for config {}", config.getId(), err);
                         oauth2AuthCache.remove(config.getId());
                     });
    }

    /**
     * @param config       the persisted OIDC configuration (must have authority set)
     * @param clientSecret resolved client secret, or null for public-client flows
     */
    private Future<OAuth2Auth> createOAuth2Auth(BaseOidcConfiguration config, String clientSecret) {
        if (config.getAuthority() == null || config.getAuthority().isBlank()) {
            return Future.failedFuture(new IllegalArgumentException(
                    "OidcConfiguration " + config.getId() + " has no authority; required for OIDC discovery"));
        }

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

        return OpenIDConnectAuth.discover(vertx, options)
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

    private Future<OAuth2Auth> getOAuth2Auth(BaseOidcConfiguration config) {
        return oauth2AuthCache.computeIfAbsent(config.getId(), id -> buildOAuth2Auth(config));
    }

    /**
     * Resolves the Key Vault secret name for confidential-client configs. Both concrete
     * config types carry one today; the {@code default} branch yields {@code null} so a
     * future public-client (PKCE-only) subclass needs no change here.
     */
    private String secretNameOf(BaseOidcConfiguration config) {
        return switch (config) {
            case OidcConfiguration c -> c.getSecretNameRef();
            case OrgSignupOidcConfiguration c -> c.getSecretNameRef();
            default -> null;
        };
    }
}
