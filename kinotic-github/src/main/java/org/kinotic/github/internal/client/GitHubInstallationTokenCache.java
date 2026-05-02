package org.kinotic.github.internal.client;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Per-node cache of GitHub installation access tokens, keyed by
 * {@code (installationId, repoId, permissions)} so a clone-scoped token is not
 * accidentally reused for ref creation. Tokens are minted lazily on first use via the
 * Caffeine async loader — concurrent callers for the same key share the in-flight
 * load, so a flurry of webhook-driven clones doesn't hammer GitHub's
 * {@code /access_tokens} endpoint.
 * <p>
 * {@code expireAfterWrite(50m)} matches the GitHub-issued lifetime; on every read we
 * additionally enforce that the remaining lifetime exceeds
 * {@link #MIN_RETURNED_TOKEN_LIFETIME}, evicting and reloading when it doesn't —
 * never hand a worker a token that's about to die mid-clone.
 */
@Slf4j
@Component
public class GitHubInstallationTokenCache {

    /** Standard scopes used in the platform. Single source so the cache key dedups them. */
    public static final Map<String, String> READ_CONTENTS = Map.of("contents", "read");
    public static final Map<String, String> WRITE_CONTENTS = Map.of("contents", "write");

    /**
     * Never return a token with less than this much life remaining; evict and reload
     * first. 10 minutes is comfortably above the slowest expected clone of a
     * multi-GB repo.
     */
    private static final Duration MIN_RETURNED_TOKEN_LIFETIME = Duration.ofMinutes(10);

    private final AsyncLoadingCache<Key, Entry> cache;

    public GitHubInstallationTokenCache(GitHubApiClient apiClient) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(50))
                .maximumSize(10_000)
                .buildAsync((Key key, java.util.concurrent.Executor executor) ->
                        apiClient.createInstallationToken(key.installationId(),
                                                          key.repoId(),
                                                          key.permissions())
                                 .map(t -> new Entry(t.token(), t.expiresAt()))
                                 .toCompletionStage()
                                 .toCompletableFuture());
    }

    /**
     * Returns a token whose remaining life exceeds {@link #MIN_RETURNED_TOKEN_LIFETIME},
     * minting a fresh one (single-flight) when the cache can't satisfy that.
     */
    public Future<Entry> get(long installationId, Long repoId, Map<String, String> permissions) {
        Key key = new Key(installationId, repoId, permissions);
        Entry peek = cache.synchronous().getIfPresent(key);
        if (peek != null && !hasEnoughLife(peek)) {
            cache.synchronous().invalidate(key);
        }
        CompletableFuture<Entry> loaded = cache.get(key);
        return Future.fromCompletionStage(loaded);
    }

    private static boolean hasEnoughLife(Entry entry) {
        return entry.expiresAt().isAfter(Instant.now().plus(MIN_RETURNED_TOKEN_LIFETIME));
    }

    /**
     * Permissions ride in the key as a {@code Map.of(...)} immutable map; equals/hashCode
     * are content-based, so {@link #READ_CONTENTS} and {@code Map.of("contents","read")}
     * collide on the same cache slot.
     */
    public record Key(long installationId, Long repoId, Map<String, String> permissions) {}
    public record Entry(String token, Instant expiresAt) {}
}
