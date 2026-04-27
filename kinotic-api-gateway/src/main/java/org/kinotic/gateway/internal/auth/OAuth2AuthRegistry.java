package org.kinotic.gateway.internal.auth;

import io.vertx.core.Future;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.secret.SecretStorageService;
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

    private final OAuth2AuthFactory factory;
    private final SecretStorageService secretStorageService;

    private final ConcurrentMap<String, Future<OAuth2Auth>> cache = new ConcurrentHashMap<>();

    /**
     * Returns a cached or freshly built {@link OAuth2Auth} for the given configuration.
     *
     * @param config      the OIDC configuration (must have a non-null id)
     * @param secretScope the secret-storage scope under which this config's client secret
     *                    is stored. Typically the scope of the entity that references this
     *                    OidcConfiguration (system, organization, application).
     */
    public Future<OAuth2Auth> get(OidcConfiguration config, String secretScope) {
        return cache.computeIfAbsent(config.getId(), id -> build(config, secretScope));
    }

    /**
     * Drops any cached entry for the given configuration id.
     */
    public void evict(String configId) {
        cache.remove(configId);
    }

    private Future<OAuth2Auth> build(OidcConfiguration config, String secretScope) {
        Future<String> secretFuture = config.getClientSecretRef() == null
                ? Future.succeededFuture(null)
                : Future.fromCompletionStage(
                        secretStorageService.getSecret(secretScope, config.getClientSecretRef()));

        return secretFuture
                .compose(secret -> factory.create(config, secret))
                .onFailure(err -> {
                    log.error("Failed to initialize OAuth2Auth for config {}", config.getId(), err);
                    cache.remove(config.getId());
                });
    }
}
