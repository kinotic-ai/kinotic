package org.kinotic.structures.internal.cache;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.lang.IgniteFuture;
import org.kinotic.continuum.api.config.ContinuumProperties;
import org.kinotic.structures.api.config.StructuresProperties;
import org.kinotic.structures.internal.cache.events.CacheEvictionEvent;
import org.kinotic.structures.internal.cache.events.CacheEvictionSource;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Event-driven cache eviction service that uses Spring Application Events
 * to decouple cache eviction from direct service dependencies.
 * 
 * This eliminates circular dependencies by allowing services to listen for
 * cache eviction events rather than being directly called.
 * 
 * Includes OpenTelemetry metrics for monitoring cache eviction health and performance.
 * 
 * Created By Nic Padilla on 2/12/25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClusterCacheEvictionService {

    private final StructuresProperties structuresProperties;
    private final ContinuumProperties continuumProperties;

    
    // move into the removal eviction service class 
    // factory class for caches so that we can have
    // a single mode that allows us to add the eviction listener


    // Lazy-initialized OpenTelemetry metrics
    private final Lazy<Meter> meter = new Lazy<>(() -> 
            GlobalOpenTelemetry.get().getMeter("structures.cache.eviction"));
    
    private final Lazy<LongCounter> evictionRequestCounter = new Lazy<>(() ->
            meter.get().counterBuilder("cache.eviction.requests")
                    .setDescription("Total cache eviction requests received")
                    .setUnit("requests")
                    .build());
    
    private final Lazy<LongCounter> clusterResultCounter = new Lazy<>(() ->
            meter.get().counterBuilder("cache.eviction.cluster.results")
                    .setDescription("Cluster cache eviction results (success or failure)")
                    .setUnit("results")
                    .build());
    
    private final Lazy<LongHistogram> clusterDurationHistogram = new Lazy<>(() ->
            meter.get().histogramBuilder("cache.eviction.cluster.duration")
                    .setDescription("Cluster cache eviction duration")
                    .setUnit("ms")
                    .ofLongs()
                    .build());
    
    private final Lazy<LongCounter> retryCounter = new Lazy<>(() ->
            meter.get().counterBuilder("cache.eviction.cluster.retries")
                    .setDescription("Number of retry attempts for cluster cache evictions")
                    .setUnit("retries")
                    .build());


    /**
     * Handle cache eviction event for cluster-wide cache eviction.
     * 
     * @param event the event containing the structure or named query to evict the
     *              caches for
     */
    @EventListener
    public void handleCacheEviction(ApplicationEvent event) {

        try {
            // we need to clear on both eviction types
            if (event instanceof CacheEvictionEvent cacheEvictionEvent
                    && cacheEvictionEvent.getEvictionSource() == CacheEvictionSource.LOCAL_MESSAGE) {
                        long timestamp = System.currentTimeMillis();
                        if(!continuumProperties.isDisableClustering()){
                            evictCachesClusterWideWithRetry(cacheEvictionEvent, timestamp);
                        }
            }

        } catch (Exception e) {
            log.error("Failed to handle cache eviction (source: {})",
                    event.getSource(), e);
        }
    }

    /**
     * Evicts caches cluster-wide with retry logic to ensure all nodes are processed.
     * Refreshes cluster topology on each retry attempt to handle node failures gracefully.
     * Tracks metrics for monitoring and alerting.
     * 
     * @param event the cache eviction event containing eviction details
     */
    private void evictCachesClusterWideWithRetry(CacheEvictionEvent event, long timestamp) {
        Exception lastException = null;
        Ignite ignite = Ignition.ignite();

        // Generate timestamp once for all retry attempts to ensure consistent versioning
        long startTime = System.currentTimeMillis();
        boolean success = false;
        int totalAttempts = 0;
        
        // Track eviction request
        Attributes requestAttributes = Attributes.builder()
                .put("eviction.type", event.getEvictionSourceType().name())
                .put("eviction.operation", event.getEvictionOperation().name())
                .put("eviction.source", event.getEvictionSource().name())
                .build();
        evictionRequestCounter.get().add(1, requestAttributes);

        log.debug("Starting {} cache eviction for: {}:{}:{} with timestamp: {}", 
                event.getEvictionSourceType(), event.getApplicationId(), 
                event.getStructureId(), event.getNamedQueryId(), timestamp);

        ClusterGroup servers = null;

        for (int attempt = 1; attempt <= structuresProperties.getClusterEviction().getMaxCacheSyncRetryAttempts(); attempt++) {
            totalAttempts = attempt;
            try {
                // Refresh cluster group on each attempt to handle topology changes
                // (e.g., nodes going down or new nodes joining)
                servers = ignite.cluster().forServers();

                if (servers.nodes().isEmpty()) {
                    log.warn("No server nodes available for cluster cache eviction (attempt {}/{})",
                            attempt, structuresProperties.getClusterEviction().getMaxCacheSyncRetryAttempts());
                    return; // No point retrying if no servers available
                }

                // Log cluster state for debugging
                if (log.isDebugEnabled()) {
                    log.debug("Attempt {}/{}: Broadcasting to {} server nodes for {}:{}:{}", 
                            attempt, structuresProperties.getClusterEviction().getMaxCacheSyncRetryAttempts(),
                            servers.nodes().size(), event.getApplicationId(), 
                            event.getStructureId(), event.getNamedQueryId());
                }

                ClusterCacheEvictionTask task = new ClusterCacheEvictionTask(
                                                        event.getEvictionSourceType(), 
                                                        event.getEvictionOperation(), 
                                                        event.getApplicationId(), 
                                                        event.getStructureId(), 
                                                        event.getNamedQueryId(), 
                                                        timestamp);
                                                        
                // Broadcast to all current server nodes using the same timestamp for idempotency
                IgniteFuture<Void> future = ignite.compute(servers).broadcastAsync(task);

                // Wait for completion with timeout
                future.get(structuresProperties.getClusterEviction().getCacheSyncTimeoutMs(), TimeUnit.MILLISECONDS);

                log.info(
                        "{} cache eviction successfully completed on all {} cluster nodes for: {}:{}:{} (timestamp: {}, attempt {}/{})",
                        event.getEvictionSourceType(), servers.nodes().size(), 
                        event.getApplicationId(), event.getStructureId(), event.getNamedQueryId(), 
                        timestamp, attempt, structuresProperties.getClusterEviction().getMaxCacheSyncRetryAttempts());

                success = true;
                break; // Success - exit retry loop

            } catch (Exception e) {
                lastException = e;
                log.warn("{} cache eviction failed on cluster for: {}:{}:{} (timestamp: {}, attempt {}/{}): {}",
                        event.getEvictionSourceType(), event.getApplicationId(), 
                        event.getStructureId(), event.getNamedQueryId(), 
                        timestamp, attempt, structuresProperties.getClusterEviction().getMaxCacheSyncRetryAttempts(), 
                        e.getMessage());

                // If this isn't the last attempt, wait before retrying
                if (attempt < structuresProperties.getClusterEviction().getMaxCacheSyncRetryAttempts()) {
                    try {
                        log.debug("Waiting {}ms before retry attempt {}", 
                                structuresProperties.getClusterEviction().getCacheSyncRetryDelayMs(), attempt + 1);
                        Thread.sleep(structuresProperties.getClusterEviction().getCacheSyncRetryDelayMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Retry interrupted for {} cache eviction: {}:{}:{} (timestamp: {})", 
                                event.getEvictionSourceType(), event.getApplicationId(), 
                                event.getStructureId(), event.getNamedQueryId(), timestamp);
                        break;
                    }
                }
            }
        }
        
        // Track retry attempts if any occurred
        if (totalAttempts > 1) {
            Attributes retryAttributes = Attributes.builder()
                    .put("eviction.type", event.getEvictionSourceType().name())
                    .put("eviction.operation", event.getEvictionOperation().name())
                    .build();
            retryCounter.get().add(totalAttempts - 1, retryAttributes);
        }
        
        // Track duration and result
        long duration = System.currentTimeMillis() - startTime;
        Attributes resultAttributes = Attributes.builder()
                .put("eviction.type", event.getEvictionSourceType().name())
                .put("eviction.operation", event.getEvictionOperation().name())
                .put("result", success ? "success" : "failure")
                .put("attempts", String.valueOf(totalAttempts))
                .build();
        
        clusterDurationHistogram.get().record(duration, resultAttributes);
        clusterResultCounter.get().add(1, resultAttributes);
        
        if (!success) {
            // If we get here, all retry attempts failed
            log.error("Failed to complete {} cache eviction on cluster for: {}:{}:{} (timestamp: {}) after {} attempts",
                    event.getEvictionSourceType(), event.getApplicationId(), 
                    event.getStructureId(), event.getNamedQueryId(), 
                    timestamp, structuresProperties.getClusterEviction().getMaxCacheSyncRetryAttempts(), lastException);
        }
    }
    
    /**
     * Simple lazy initialization helper to avoid issues with OTEL initialization order
     */
    private static class Lazy<T> {
        private final Supplier<T> supplier;
        private volatile T value;
        
        Lazy(Supplier<T> supplier) {
            this.supplier = supplier;
        }
        
        T get() {
            if (value == null) {
                synchronized (this) {
                    if (value == null) {
                        value = supplier.get();
                    }
                }
            }
            return value;
        }
    }

}