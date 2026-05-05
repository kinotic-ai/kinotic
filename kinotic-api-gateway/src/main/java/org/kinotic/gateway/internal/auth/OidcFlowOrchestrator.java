package org.kinotic.gateway.internal.auth;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2AuthorizationURL;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.Oauth2Credentials;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.gateway.internal.endpoints.rest.RedirectFlowSessionSupport;
import org.kinotic.os.api.model.iam.BaseOidcConfiguration;
import org.kinotic.os.api.model.iam.OidcConfiguration;
import org.kinotic.os.api.model.iam.OrgSignupOidcConfiguration;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Owns the OAuth 2.0 / OIDC dance shared by every handler that bounces the user out to
 * an IdP and back: state/nonce/PKCE generation, session storage, callback validation,
 * code exchange, claim extraction, issuer validation. Handlers compose it with their
 * own {@link SessionKeys} namespace and a per-route config resolver — the orchestrator
 * itself knows nothing about IamUser provisioning, JWT minting, or which entity table
 * the configuration came from.
 *
 * <p>{@link OAuth2AuthRegistry} caches per-config {@link OAuth2Auth} instances; the
 * orchestrator delegates discovery + secret resolution to it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OidcFlowOrchestrator {

    private final OAuth2AuthRegistry oauth2AuthRegistry;

    /**
     * Generates state/nonce/PKCE, stores them on the session under the given keys, builds
     * the IdP authorization URL using the supplied callback URL, and returns the URL.
     *
     * @param extras values to stash on the session for the callback to retrieve later
     *               (e.g. {@code orgId} for SSO flows). Keys must be enumerated in
     *               {@link SessionKeys#extraKeys()} so the callback can capture them.
     */
    public Future<String> startFlow(RoutingContext ctx,
                                    BaseOidcConfiguration config,
                                    SessionKeys keys,
                                    String callbackUrl,
                                    Map<String, String> extras) {
        String state = RedirectFlowSessionSupport.randomUrlSafe(32);
        String nonce = RedirectFlowSessionSupport.randomUrlSafe(32);
        String pkceVerifier = RedirectFlowSessionSupport.randomUrlSafe(64);
        String pkceChallenge = RedirectFlowSessionSupport.s256Challenge(pkceVerifier);

        Session session = ctx.session();
        session.regenerateId();
        session.put(keys.state(), state);
        session.put(keys.nonce(), nonce);
        session.put(keys.pkce(), pkceVerifier);
        session.put(keys.configId(), config.getId());
        if (extras != null) {
            extras.forEach(session::put);
        }

        return oauth2AuthRegistry.get(config, secretNameOf(config))
                                 .map(oauth2 -> oauth2.authorizeURL(
                                         new OAuth2AuthorizationURL()
                                                 .setRedirectUri(callbackUrl)
                                                 .setScopes(List.of("openid", "email", "profile"))
                                                 .setState(state)
                                                 .setCodeChallenge(pkceChallenge)
                                                 .setCodeChallengeMethod("S256")
                                                 .putAdditionalParameter("nonce", nonce)));
    }

    /**
     * Validates the callback (state match, no IdP error), exchanges the code, validates
     * the issuer, and returns the configuration along with the verified id_token claims.
     * The handler decides what to do with the claims (look up an IamUser, create a
     * {@code PendingRegistration}, etc.).
     *
     * <p>The session is consumed regardless of outcome — replay protection.
     *
     * @param configResolver looks up the config by the {@code :configId} path param from
     *                       the appropriate service for this route. Must return a
     *                       {@link CompletableFuture} resolving to {@code null} when the
     *                       id is unknown.
     */
    public <C extends BaseOidcConfiguration> Future<CallbackResult<C>> handleCallback(
            RoutingContext ctx,
            String pathConfigId,
            SessionKeys keys,
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
            return Future.failedFuture(new OidcCallbackException("invalid_callback"));
        }

        Session session = ctx.session();
        String expectedState = session.get(keys.state());
        String pkceVerifier = session.get(keys.pkce());
        String sessionConfigId = session.get(keys.configId());
        Map<String, String> extras = new HashMap<>();
        for (String extraKey : keys.extraKeys()) {
            String value = session.get(extraKey);
            if (value != null) extras.put(extraKey, value);
        }
        session.destroy();

        if (expectedState == null || !expectedState.equals(state)
                || sessionConfigId == null || !sessionConfigId.equals(pathConfigId)) {
            log.warn("OIDC callback state mismatch for configId={}", pathConfigId);
            return Future.failedFuture(new OidcCallbackException("state_mismatch"));
        }

        return Future.fromCompletionStage(configResolver.apply(pathConfigId))
                     .compose(config -> {
                         if (config == null) {
                             return Future.failedFuture(new OidcCallbackException("config_not_found"));
                         }
                         return oauth2AuthRegistry.get(config, secretNameOf(config))
                                                  .compose(oauth2 -> exchangeCode(oauth2, code, callbackUrl, pkceVerifier))
                                                  .map(user -> {
                                                      Map<String, Object> claims = flattenClaims(user);
                                                      if (!OAuth2AuthFactory.isIssuerValid(claims, config.getAuthority())) {
                                                          log.warn("OIDC issuer validation failed for config {}: iss={}, tid={}",
                                                                   config.getId(), claims.get("iss"), claims.get("tid"));
                                                          throw new OidcCallbackException("invalid_token");
                                                      }
                                                      return new CallbackResult<>(config, claims, extras);
                                                  });
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
    public static Map<String, Object> flattenClaims(User user) {
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

    public static String stringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value == null ? null : value.toString();
    }

    public static String firstPresent(Map<String, Object> claims, String... names) {
        for (String name : names) {
            String value = stringClaim(claims, name);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    /**
     * Decoupling point: only {@link OidcConfiguration} and {@link OrgSignupOidcConfiguration}
     * carry a client secret — {@link org.kinotic.os.api.model.iam.SystemOidcConfiguration}
     * is a public-client (PKCE only). Pattern-matching here keeps the registry signature
     * uniform across all three subclasses.
     */
    private static String secretNameOf(BaseOidcConfiguration config) {
        return switch (config) {
            case OidcConfiguration c -> c.getSecretNameRef();
            case OrgSignupOidcConfiguration c -> c.getSecretNameRef();
            default -> null;
        };
    }
}
