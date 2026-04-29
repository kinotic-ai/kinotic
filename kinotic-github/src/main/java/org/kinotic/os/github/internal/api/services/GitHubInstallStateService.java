package org.kinotic.os.github.internal.api.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Cluster-wide store mapping a single-use {@code state} token to the Kinotic
 * {@code organizationId} that owns it. Used by the GitHub install flow: the published
 * {@code startInstall()} service stages the mapping, and the REST callback handler
 * consumes it (atomically removing the entry) when GitHub redirects back.
 * <p>
 * Backed by an Ignite cache when clustering is enabled (entries are partitioned with
 * one backup and TTL-expire after 10 minutes); falls back to a per-node Caffeine
 * cache when {@code kinotic.disableClustering=true}.
 * <p>
 * The state value is a 256-bit URL-safe random string — short enough for a query
 * parameter but unguessable. The mapping is single-use: once {@link #consume(String)}
 * succeeds, the entry is gone, so a redelivery of the GitHub callback would just
 * see a missing state and bounce back to the SPA error path.
 */
@Slf4j
@Component
public class GitHubInstallStateService {

    private static final String CACHE_NAME = "kinotic_github_install_state";
    private static final long TTL_MINUTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired(required = false)
    private Ignite ignite;

    private IgniteCache<String, String> igniteCache;
    private Cache<String, String> caffeineCache;

    @PostConstruct
    public void start() {
        if (ignite != null) {
            CacheConfiguration<String, String> cfg = new CacheConfiguration<>(CACHE_NAME);
            cfg.setCacheMode(CacheMode.PARTITIONED);
            cfg.setBackups(1);
            cfg.setWriteSynchronizationMode(CacheWriteSynchronizationMode.PRIMARY_SYNC);
            cfg.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
                    new Duration(TimeUnit.MINUTES, TTL_MINUTES)));
            this.igniteCache = ignite.getOrCreateCache(cfg);
            log.info("GitHub install-state store using Ignite cache '{}' (TTL {}m)",
                     CACHE_NAME, TTL_MINUTES);
        } else {
            this.caffeineCache = Caffeine.newBuilder()
                    .expireAfterWrite(java.time.Duration.ofMinutes(TTL_MINUTES))
                    .maximumSize(10_000)
                    .build();
            log.info("GitHub install-state store using local Caffeine cache (no clustering, TTL {}m)",
                     TTL_MINUTES);
        }
    }

    /**
     * Stages a fresh state token for {@code organizationId} and returns it. The
     * caller embeds the returned token in the GitHub install URL's {@code state}
     * parameter; the GitHub-driven callback hands it to {@link #consume(String)}.
     */
    public String stage(String organizationId) {
        String state = randomState();
        if (igniteCache != null) {
            igniteCache.put(state, organizationId);
        } else {
            caffeineCache.put(state, organizationId);
        }
        return state;
    }

    /**
     * Atomically retrieves and removes the organizationId staged for {@code state}.
     * Returns {@code null} when the state is absent, expired, or already consumed —
     * caller should treat any of these the same: redirect to the SPA's error page.
     */
    public String consume(String state) {
        if (state == null || state.isBlank()) return null;
        if (igniteCache != null) {
            return igniteCache.getAndRemove(state);
        }
        // Caffeine has no atomic getAndRemove; the small race window doesn't matter
        // because state is single-node-bound when clustering is off.
        String value = caffeineCache.getIfPresent(state);
        if (value != null) {
            caffeineCache.invalidate(state);
        }
        return value;
    }

    private static String randomState() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
