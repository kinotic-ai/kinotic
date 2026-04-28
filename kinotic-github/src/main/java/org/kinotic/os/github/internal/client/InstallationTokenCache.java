package org.kinotic.os.github.internal.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.github.api.config.KinoticGithubProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-node cache of GitHub installation access tokens, keyed by
 * {@code (installationId, repoId, permissionsKey)} so a clone-scoped token is not
 * accidentally reused for ref creation. {@code expireAfterWrite(50m)} matches the
 * GitHub-issued lifetime; on every read we additionally enforce that the remaining
 * lifetime exceeds {@code minReturnedTokenLifetime}, refreshing synchronously when
 * not. Concurrent requests for the same key coalesce via a per-key promise map.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstallationTokenCache {

    /** Standard scopes used in the platform. Single source so the cache key dedups them. */
    public static final Map<String, String> READ_CONTENTS = Map.of("contents", "read");
    public static final Map<String, String> WRITE_CONTENTS = Map.of("contents", "write");

    private final KinoticGithubProperties properties;
    private final GitHubApiClient apiClient;

    private final Cache<Key, Entry> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(50))
            .maximumSize(10_000)
            .build();
    private final ConcurrentHashMap<Key, Promise<Entry>> inflight = new ConcurrentHashMap<>();

    /**
     * Returns a token whose remaining life exceeds
     * {@link KinoticGithubProperties#getGithub() minReturnedTokenLifetime}, minting a
     * new one if cache state can't satisfy that. Single-flight: concurrent callers for
     * the same key share one mint.
     */
    public Future<Entry> get(String installationId, String repoId, Map<String, String> permissions) {
        Key key = new Key(installationId, repoId, permissionKey(permissions));
        Entry cached = cache.getIfPresent(key);
        if (cached != null && hasEnoughLife(cached)) {
            return Future.succeededFuture(cached);
        }
        // Coalesce concurrent mints for the same key.
        Promise<Entry> promise = Promise.promise();
        Promise<Entry> existing = inflight.putIfAbsent(key, promise);
        if (existing != null) {
            return existing.future();
        }
        apiClient.createInstallationToken(installationId, repoId, permissions)
                .onComplete(ar -> {
                    inflight.remove(key);
                    if (ar.succeeded()) {
                        Entry entry = new Entry(ar.result().token(), ar.result().expiresAt());
                        cache.put(key, entry);
                        promise.complete(entry);
                    } else {
                        promise.fail(ar.cause());
                    }
                });
        return promise.future();
    }

    private boolean hasEnoughLife(Entry entry) {
        Duration min = properties.getGithub().getMinReturnedTokenLifetime();
        return entry.expiresAt().isAfter(Instant.now().plus(min));
    }

    /**
     * Renders a permissions map to a stable cache-key fragment so {@code {"contents":"read"}}
     * and {@code {"contents":"read"}} produce the same key regardless of HashMap order.
     */
    static String permissionKey(Map<String, String> permissions) {
        if (permissions == null || permissions.isEmpty()) return "";
        // TreeMap-style stable order without pulling another import.
        Map<String, String> sorted = new HashMap<>(permissions);
        return sorted.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    public record Key(String installationId, String repoId, String permissionsKey) {}
    public record Entry(String token, Instant expiresAt) {}
}
