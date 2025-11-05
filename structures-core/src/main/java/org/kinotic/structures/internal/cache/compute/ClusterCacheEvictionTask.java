package org.kinotic.structures.internal.cache.compute;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.resources.SpringResource;
import org.kinotic.structures.api.services.StructureService;
import org.kinotic.structures.api.services.NamedQueriesService;
import org.kinotic.structures.internal.cache.events.CacheEvictionEvent;
import org.kinotic.structures.internal.cache.events.EvictionSourceOperation;
import org.kinotic.structures.internal.cache.events.EvictionSourceType;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.TimeUnit;

/**
 * Simple Ignite Compute Grid task for cluster-wide cache eviction.
 * Uses IDs to avoid serialization issues.
 * Includes timestamp-based duplicate prevention with auto-expiry.
 */
@Slf4j
@RequiredArgsConstructor
public class ClusterCacheEvictionTask implements IgniteRunnable {

    // Track processed evictions to prevent duplicates with auto-expiry
    // Key: evictionType:operation:applicationId:structureId:namedQueryId:timestamp, Value: timestamp
    private static final Cache<String, Long> processedEvictions = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(10000)
            .build();

    @SpringResource(resourceClass = ApplicationEventPublisher.class)
    private ApplicationEventPublisher eventPublisher;

    @SpringResource(resourceClass = StructureService.class)
    private StructureService structureService;

    @SpringResource(resourceClass = NamedQueriesService.class)
    private NamedQueriesService namedQueriesService;

    private final EvictionSourceType evictionSourceType; // "STRUCTURE" or "NAMED_QUERY"
    private final EvictionSourceOperation evictionOperation; // "MODIFY" or "DELETE"
    private final String applicationId;
    private final String structureId;
    private final String namedQueryId;
    private final long timestamp; // Timestamp to prevent duplicate processing

    @Override
    public void run() {
        try {
            // Create unique key for this eviction request
            String evictionKey = "";
            if(namedQueryId != null){
                evictionKey = evictionSourceType + ":" + evictionOperation + ":" + applicationId + ":" + structureId + ":" + namedQueryId + ":" + timestamp;
            } else {
                evictionKey = evictionSourceType + ":" + evictionOperation + ":" + applicationId + ":" + structureId + ":" + timestamp;
            }
            
            // Check if this eviction has already been processed
            Long existingTimestamp = processedEvictions.getIfPresent(evictionKey);
            if (existingTimestamp != null && existingTimestamp.equals(timestamp)) {
                log.debug("Cache eviction already processed for {}:{} {} {} {} (timestamp: {})", evictionSourceType, evictionOperation, applicationId, structureId, namedQueryId, timestamp);
                return; // Skip duplicate processing
            }
            
            if (EvictionSourceType.STRUCTURE == evictionSourceType) {
                log.debug("Executing Structure cache eviction {} on cluster node for ID: {} {} (timestamp: {})", evictionOperation, applicationId, structureId, timestamp);
                
                if (structureId != null) {

                    if(evictionOperation == EvictionSourceOperation.MODIFY){
                        eventPublisher.publishEvent(CacheEvictionEvent.clusterModifiedStructure(applicationId, structureId));
                    } else if(evictionOperation == EvictionSourceOperation.DELETE){
                        eventPublisher.publishEvent(CacheEvictionEvent.clusterDeletedStructure(applicationId, structureId));
                    } else {
                        throw new IllegalArgumentException("Invalid eviction operation: " + evictionOperation);
                    }
                    
                    // Mark as processed
                    processedEvictions.put(evictionKey, timestamp);
                    log.debug("Successfully processed Structure cache eviction {} for ID: {} {} (timestamp: {})", evictionOperation, applicationId, structureId, timestamp);
                } else {
                    log.warn("Structure not found for cache eviction: {} {}", applicationId, structureId);
                    throw new RuntimeException("Structure not found: " + structureId);
                }
                
            } else if (EvictionSourceType.NAMED_QUERY == evictionSourceType) {
                log.debug("Executing NamedQuery cache eviction {}on cluster node for ID: {} {} (timestamp: {})", evictionOperation, applicationId, namedQueryId, timestamp);
                
                if (namedQueryId != null) {

                    if(evictionOperation == EvictionSourceOperation.MODIFY){
                        eventPublisher.publishEvent(CacheEvictionEvent.clusterModifiedNamedQuery(applicationId, structureId, namedQueryId));
                    } else if(evictionOperation == EvictionSourceOperation.DELETE){
                        eventPublisher.publishEvent(CacheEvictionEvent.clusterDeletedNamedQuery(applicationId, structureId, namedQueryId));
                    } else {
                        throw new IllegalArgumentException("Invalid eviction operation: " + evictionOperation);
                    }
                    
                    // Mark as processed
                    processedEvictions.put(evictionKey, timestamp);
                    log.debug("Successfully processed NamedQuery cache eviction {} for ID: {} {} (timestamp: {})", evictionOperation, applicationId, namedQueryId, timestamp);
                } else {
                log.warn("NamedQuery not found for cache eviction: {} {} {}", applicationId, structureId, namedQueryId);
                    throw new RuntimeException("NamedQuery not found: " + applicationId + ":" + structureId + ":" + namedQueryId);
                }
            } else {
                throw new IllegalArgumentException("Invalid eviction type: " + evictionSourceType);
            }
        } catch (Exception e) {
            log.error("Cache eviction failed on cluster node for {}: {} {} (timestamp: {})", evictionSourceType, applicationId, structureId, namedQueryId, timestamp, e);
            throw new RuntimeException("Cache eviction failed", e);
        }
    }
}
