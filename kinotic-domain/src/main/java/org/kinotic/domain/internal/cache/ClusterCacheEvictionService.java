package org.kinotic.domain.internal.cache;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.lang.IgniteFuture;
import org.kinotic.domain.api.cache.CacheEvictionEvent;
import org.kinotic.domain.api.cache.CacheEvictionSource;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
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
public class ClusterCacheEvictionService {

    private static final int MAX_CACHE_SYNC_RETRY_ATTEMPTS = 3;
    private static final long CACHE_SYNC_RETRY_DELAY_MS = 1000L;
    private static final long CACHE_SYNC_TIMEOUT_MS = 30000L;

    private final Ignite ignite;

    private final LongCounter evictionRequestCounter;
    private final LongCounter clusterResultCounter;
    private final LongHistogram clusterDurationHistogram;
    private final LongCounter retryCounter;

    public ClusterCacheEvictionService(Ignite ignite,
                                       OpenTelemetry openTelemetry) {
        this.ignite = ignite;

        Meter meter = openTelemetry.getMeter("kinotic.cache.eviction");

        evictionRequestCounter = meter.counterBuilder("cache.eviction.requests")
                .setDescription("Total cache eviction requests received")
                .setUnit("requests")
                .build();

        clusterResultCounter = meter.counterBuilder("cache.eviction.cluster.results")
                .setDescription("Cluster cache eviction results (success or failure)")
                .setUnit("results")
                .build();

        clusterDurationHistogram = meter.histogramBuilder("cache.eviction.cluster.duration")
                .setDescription("Cluster cache eviction duration")
                .setUnit("ms")
                .ofLongs()
                .build();

        retryCounter = meter.counterBuilder("cache.eviction.cluster.retries")
                .setDescription("Number of retry attempts for cluster cache evictions")
                .setUnit("retries")
                .build();
    }

    /**
     * Handle cache eviction event for cluster-wide cache eviction.
     *
     * @param event the event containing the entity definition or named query to evict the
     *              caches for
     */
    @EventListener
    public void handleCacheEviction(CacheEvictionEvent event) {

        try {
            // we need to clear on both eviction types
            if (event.getEvictionSource() == CacheEvictionSource.LOCAL_MESSAGE) {
                evictCachesClusterWideWithRetry(event, System.currentTimeMillis());
            }

        } catch (Exception e) {
            log.error("Failed to handle cache eviction (source: {})",
                    event.getSource(), e);
        }
    }

    /**
     * Evicts caches cluster-wide with retry logic to ensure all nodes are
     * processed.
     * Refreshes cluster topology on each retry attempt to handle node failures
     * gracefully.
     * Tracks metrics for monitoring and alerting.
     *
     * @param event the cache eviction event containing eviction details
     */
    private void evictCachesClusterWideWithRetry(CacheEvictionEvent event, long timestamp) {
        Exception lastException = null;

        // Generate timestamp once for all retry attempts to ensure consistent
        // versioning
        long startTime = System.currentTimeMillis();
        boolean success = false;
        int totalAttempts = 0;

        // Track eviction request
        Attributes requestAttributes = Attributes.builder()
                .put("eviction.type", event.getEvictionSourceType().name())
                .put("eviction.operation", event.getEvictionOperation().name())
                .put("eviction.source", event.getEvictionSource().name())
                .build();
        evictionRequestCounter.add(1, requestAttributes);

        log.trace("Starting {} cache eviction for: {}:{}:{}:{} with timestamp: {}",
                  event.getEvictionSourceType(), event.getOrganizationId(), event.getApplicationId(),
                  event.getEntityDefinitionId(), event.getNamedQueryId(), timestamp);

        ClusterGroup servers = null;

        for (int attempt = 1; attempt <= MAX_CACHE_SYNC_RETRY_ATTEMPTS; attempt++) {
            totalAttempts = attempt;
            try {
                // Refresh cluster group on each attempt to handle topology changes
                // (e.g., nodes going down or new nodes joining)
                servers = ignite.cluster().forServers();

                if (servers.nodes().isEmpty()) {
                    log.warn("No server nodes available for cluster cache eviction (attempt {}/{})",
                             attempt, MAX_CACHE_SYNC_RETRY_ATTEMPTS);
                    return; // No point retrying if no servers available
                }

                // Log cluster state for debugging
                log.trace("Attempt {}/{}: Broadcasting to {} server nodes for {}:{}:{}:{}",
                          attempt, MAX_CACHE_SYNC_RETRY_ATTEMPTS,
                          servers.nodes().size(), event.getOrganizationId(), event.getApplicationId(),
                          event.getEntityDefinitionId(), event.getNamedQueryId());

                ClusterCacheEvictionTask task = new ClusterCacheEvictionTask(
                        event.getEvictionSourceType(),
                        event.getEvictionOperation(),
                        event.getOrganizationId(),
                        event.getApplicationId(),
                        event.getEntityDefinitionId(),
                        event.getNamedQueryId(),
                        timestamp);

                // Broadcast to all current server nodes; a node that already evicted on an
                // earlier attempt evicts again, which is idempotent
                IgniteFuture<Void> future = ignite.compute(servers).broadcastAsync(task);

                // Wait for completion with timeout
                future.get(CACHE_SYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                log.debug(
                        "{} cache eviction successfully completed on all {} cluster nodes for: {}:{}:{}:{} (timestamp: {}, attempt {}/{})",
                        event.getEvictionSourceType(), servers.nodes().size(), event.getOrganizationId(),
                        event.getApplicationId(), event.getEntityDefinitionId(), event.getNamedQueryId(),
                        timestamp, attempt, MAX_CACHE_SYNC_RETRY_ATTEMPTS);

                success = true;
                break; // Success - exit retry loop

            } catch (Exception e) {
                lastException = e;
                log.warn("{} cache eviction failed on cluster for: {}:{}:{}:{} (timestamp: {}, attempt {}/{}): {}",
                         event.getEvictionSourceType(), event.getOrganizationId(), event.getApplicationId(),
                         event.getEntityDefinitionId(), event.getNamedQueryId(),
                         timestamp, attempt, MAX_CACHE_SYNC_RETRY_ATTEMPTS,
                         e.getMessage());

                // If this isn't the last attempt, wait before retrying
                if (attempt < MAX_CACHE_SYNC_RETRY_ATTEMPTS) {
                    try {
                        log.debug("Waiting {}ms before retry attempt {}",
                                  CACHE_SYNC_RETRY_DELAY_MS, attempt + 1);
                        Thread.sleep(CACHE_SYNC_RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Retry interrupted for {} cache eviction: {}:{}:{}:{} (timestamp: {})",
                                  event.getEvictionSourceType(), event.getOrganizationId(), event.getApplicationId(),
                                  event.getEntityDefinitionId(), event.getNamedQueryId(), timestamp);
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
            retryCounter.add(totalAttempts - 1, retryAttributes);
        }

        // Track duration and result
        long duration = System.currentTimeMillis() - startTime;
        Attributes resultAttributes = Attributes.builder()
                .put("eviction.type", event.getEvictionSourceType().name())
                .put("eviction.operation", event.getEvictionOperation().name())
                .put("result", success ? "success" : "failure")
                .put("attempts", String.valueOf(totalAttempts))
                .build();

        clusterDurationHistogram.record(duration, resultAttributes);
        clusterResultCounter.add(1, resultAttributes);

        if (!success) {
            // If we get here, all retry attempts failed
            log.error("Failed to complete {} cache eviction on cluster for: {}:{}:{}:{} (timestamp: {}) after {} attempts",
                      event.getEvictionSourceType(), event.getOrganizationId(), event.getApplicationId(),
                      event.getEntityDefinitionId(), event.getNamedQueryId(),
                      timestamp, MAX_CACHE_SYNC_RETRY_ATTEMPTS, lastException);
        }
    }

}