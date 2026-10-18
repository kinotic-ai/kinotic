package org.kinotic.appserver.internal.api.services.security;

import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Cluster-wide store of the social sign-ins in transit between the app server's shared callback host and the
 * application's own API host, each under a single-use code. The callback host stages a verified sign-in and
 * sends the browser to the application's host with the code; that host consumes the code once, so a
 * replayed or stale completion finds nothing and fails. Entries expire after one minute, the time a redirect
 * takes.
 */
@Slf4j
@Component
public class SocialLoginHandoffService {

    private static final String CACHE_NAME = "kinotic_app_social_login_handoff";
    private static final long TTL_MINUTES = 1;

    private final IgniteCache<String, SocialLoginHandoff> cache;

    public SocialLoginHandoffService(Ignite ignite) {
        CacheConfiguration<String, SocialLoginHandoff> cfg = new CacheConfiguration<>(CACHE_NAME);
        cfg.setCacheMode(CacheMode.PARTITIONED);
        cfg.setBackups(1);
        cfg.setWriteSynchronizationMode(CacheWriteSynchronizationMode.PRIMARY_SYNC);
        cfg.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.MINUTES, TTL_MINUTES)));
        this.cache = ignite.getOrCreateCache(cfg);
        log.info("Social login handoff store using Ignite cache '{}' (TTL {}m)", CACHE_NAME, TTL_MINUTES);
    }

    /**
     * Stages {@code handoff} and returns the single-use code it is held under, a 256-bit URL-safe random
     * string for the completion URL.
     */
    public String stage(SocialLoginHandoff handoff) {
        String code = DomainUtil.generateUrlSafeToken(32);
        cache.put(code, handoff);
        return code;
    }

    /**
     * Atomically retrieves and removes the sign-in staged under {@code code}, or {@code null} when the code is
     * unknown, expired, or already consumed.
     */
    public SocialLoginHandoff consume(String code) {
        return code == null || code.isBlank() ? null : cache.getAndRemove(code);
    }
}
