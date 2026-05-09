package org.kinotic.gateway.internal.auth;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.providers.AzureADAuth;
import io.vertx.ext.auth.oauth2.providers.OpenIDConnectAuth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.secret.SecretReferenceResolver;
import org.kinotic.os.api.model.iam.BaseOidcConfiguration;
import org.kinotic.os.api.model.iam.OidcConfiguration;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-{@link OidcConfiguration} cache of {@link OAuth2Auth} instances. Discovery is expensive
 * (one network call per provider at startup, plus JWKS fetching), so successful results are
 * cached by configuration id. On failure the entry is evicted so the next caller retries
 * rather than inheriting a stuck failed future.
 * <p>
 * Callers should invoke {@link #evict(String)} when an {@link OidcConfiguration} is updated
 * or deleted so the cached {@code OAuth2Auth} is rebuilt on next use.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthRegistry {

    private final ConcurrentMap<String, Future<OAuth2Auth>> cache = new ConcurrentHashMap<>();
    private final SecretReferenceResolver secretReferenceResolver;
    private final Vertx vertx;


    /**
     * Returns a cached or freshly built {@link OAuth2Auth} for the given configuration.
     * The client secret (when {@code secretName} is non-null) is fetched from the
     * platform secret store via {@link SecretReferenceResolver} on first build and
     * baked into the cached {@code OAuth2Auth}; subsequent lookups don't re-fetch.
     *
     * <p>The caller supplies the secret name explicitly so the registry stays
     * decoupled from which {@link BaseOidcConfiguration} subclasses carry secrets —
     * pass {@code null} for public-client flows (e.g. {@code SystemOidcConfiguration}).
     */
    public Future<OAuth2Auth> get(BaseOidcConfiguration config, String secretName) {
        return cache.computeIfAbsent(config.getId(), id -> build(config, secretName));
    }

    /**
     * Drops any cached entry for the given configuration id.
     */
    public void evict(String configId) {
        cache.remove(configId);
    }

    private Future<OAuth2Auth> build(BaseOidcConfiguration config, String secretName) {
        return Future.fromCompletionStage(secretReferenceResolver.resolve(secretName))
                     .compose(secret -> create(config, secret))
                     .onFailure(err -> {
                         log.error("Failed to initialize OAuth2Auth for config {}", config.getId(), err);
                         cache.remove(config.getId());
                     });
    }

    /**
     * @param config       the persisted OIDC configuration (must have authority set)
     * @param clientSecret resolved client secret, or null for public-client flows
     */
    private Future<OAuth2Auth> create(BaseOidcConfiguration config, String clientSecret) {
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

//        AzureADAuth

        return OpenIDConnectAuth.discover(vertx, options)
                                .map(oauth -> {
                                    if (options.getJWTOptions() != null) {
                                        // Discovery mutates JWTOptions.issuer with the
                                        // {tenantid}-templated string — Vert.x would then fail every
                                        // per-JWT validation because the real JWT carries the
                                        // substituted tid. Clear it for the multi-tenant case; we
                                        // re-validate the issuer ourselves via {@link #isIssuerValid}
                                        // post-exchange using the JWT's tid claim (which is signed
                                        // by the JWKS Vert.x already verified against).
                                        String jwtIssuer = options.getJWTOptions().getIssuer();
                                        if (jwtIssuer != null && jwtIssuer.contains("{tenantid}")) {
                                            options.getJWTOptions().setIssuer(null);
                                        }
                                        // Belt-and-suspenders audience check: when configured, push
                                        // the expected audience into Vert.x's JWTOptions so the aud
                                        // claim is validated during token-exchange JWT processing.
                                        // The orchestrator re-validates post-exchange via
                                        // {@link #isAudienceValid} so coverage holds across Vert.x
                                        // version drift.
                                        if (config.getAudience() != null && !config.getAudience().isBlank()) {
                                            options.getJWTOptions().addAudience(config.getAudience());
                                        }
                                    }
                                    return oauth;
                                });
    }

}
