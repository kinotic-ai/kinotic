package org.kinotic.gateway.internal.auth;

import io.vertx.core.Future;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.secret.SecretReferenceResolver;
import org.kinotic.os.api.model.iam.BaseOidcConfiguration;
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

    private final OAuth2AuthFactory factory;
    private final SecretReferenceResolver secretReferenceResolver;

    private final ConcurrentMap<String, Future<OAuth2Auth>> cache = new ConcurrentHashMap<>();

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
                     .compose(secret -> factory.create(config, secret))
                     .onFailure(err -> {
                         log.error("Failed to initialize OAuth2Auth for config {}", config.getId(), err);
                         cache.remove(config.getId());
                     });
    }
}
