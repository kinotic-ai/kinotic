package org.kinotic.persistence.internal.cache;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;

import org.kinotic.auth.internal.services.DefaultCaffeineCacheFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Spring-managed cache for tracking processed eviction requests.
 * This bean holds the cache instance and is injected into {@link ClusterCacheEvictionTask}
 * via Ignite's {@code @SpringResource} annotation.
 * <p>
 * The cache tracks eviction keys with timestamps to prevent duplicate processing
 * when eviction tasks are broadcast across the cluster.
 * <p>
 * Created by Nic Padilla on 1/2/26.
 */
@Slf4j
@Component
public class ProcessedEvictionsCache {

    private final Cache<String, Long> cache;

    public ProcessedEvictionsCache(DefaultCaffeineCacheFactory cacheFactory) {
        this.cache = cacheFactory.<String, Long>newBuilder()
                .name("processedEvictions")
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(10000)
                .removalListener((key, value, cause) -> 
                    log.trace("Eviction tracking entry removed: key={}, cause={}", key, cause))
                .build();
    }

    /**
     * Gets the timestamp for a previously processed eviction key.
     *
     * @param evictionKey the unique eviction key
     * @return the timestamp if present, null otherwise
     */
    public Long getIfPresent(String evictionKey) {
        return cache.getIfPresent(evictionKey);
    }

    /**
     * Marks an eviction key as processed with the given timestamp.
     *
     * @param evictionKey the unique eviction key
     * @param timestamp the processing timestamp
     */
    public void put(String evictionKey, Long timestamp) {
        cache.put(evictionKey, timestamp);
    }
}

