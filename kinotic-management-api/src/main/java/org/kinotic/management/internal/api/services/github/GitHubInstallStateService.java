package org.kinotic.management.internal.api.services.github;

import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.springframework.stereotype.Component;

import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Cluster-wide store mapping a single-use {@code state} token to the
 * {@link StagedInstall} that owns it. The published {@code startInstall()} stages an
 * entry; the SPA's post-install callback hands the same {@code state} to
 * {@code completeInstall()}, which atomically pops it.
 * <p>
 * Backed by an Ignite cache: entries are partitioned with one backup and TTL-expire
 * after 10 minutes.
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

    private final IgniteCache<String, StagedInstall> cache;

    public GitHubInstallStateService(Ignite ignite) {
        CacheConfiguration<String, StagedInstall> cfg = new CacheConfiguration<>(CACHE_NAME);
        cfg.setCacheMode(CacheMode.PARTITIONED);
        cfg.setBackups(1);
        cfg.setWriteSynchronizationMode(CacheWriteSynchronizationMode.PRIMARY_SYNC);
        cfg.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
                new Duration(TimeUnit.MINUTES, TTL_MINUTES)));
        this.cache = ignite.getOrCreateCache(cfg);
        log.info("GitHub install-state store using Ignite cache '{}' (TTL {}m)", CACHE_NAME, TTL_MINUTES);
    }

    /**
     * Stages a fresh state token bound to {@code staged} and returns it. The caller
     * embeds the returned token in the GitHub install URL's {@code state} parameter;
     * the SPA's post-install callback hands it to {@link #consume(String)}.
     */
    public String stage(StagedInstall staged) {
        String state = randomState();
        cache.put(state, staged);
        return state;
    }

    /**
     * Atomically retrieves and removes the {@link StagedInstall} for {@code state}.
     * Returns {@code null} when the state is absent, expired, or already consumed —
     * caller should treat any of these the same: surface an error to the SPA.
     */
    public StagedInstall consume(String state) {
        if (state == null || state.isBlank()) return null;
        return cache.getAndRemove(state);
    }

    private static String randomState() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
