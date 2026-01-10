package org.kinotic.structures.internal.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.resources.SpringResource;
import org.kinotic.structures.api.services.NamedQueriesService;
import org.kinotic.structures.api.services.StructureService;
import org.kinotic.structures.internal.cache.events.CacheEvictionEvent;
import org.kinotic.structures.internal.cache.events.EvictionSourceOperation;
import org.kinotic.structures.internal.cache.events.EvictionSourceType;
import org.springframework.context.ApplicationEventPublisher;
import org.kinotic.structures.internal.config.CacheEvictionConfiguration;

/**
 * Simple Ignite Compute Grid task for cluster-wide cache eviction.
 * Uses IDs to avoid serialization issues.
 * Includes timestamp-based duplicate prevention with auto-expiry.
 * 
 * Spring Resource Injection:
 * This task uses Apache Ignite's {@link SpringResource} annotation to inject Spring beans.
 * Since Continuum uses {@code IgniteSpring.start(IgniteConfiguration, ApplicationContext)},
 * Spring beans are automatically injected into fields annotated with {@code @SpringResource}.
 * 
 * Important:
 * - Fields must be marked as {@code transient} to prevent serialization issues
 * - Use {@code resourceClass} to inject by type, or {@code resourceName} to inject by bean name
 * - Injection happens automatically when the task is executed on each cluster node
 * - Each node must have the Spring ApplicationContext available (handled by Continuum)
 * - Note: We inject {@link ApplicationEventPublisher} using bean name "applicationEventPublisher"
 *   which is provided by {@link CacheEvictionConfiguration#applicationEventPublisher()}
 *   This bean wraps the ApplicationContext and makes it available for Ignite task injection
 */
@Slf4j
@RequiredArgsConstructor
public class ClusterCacheEvictionTask implements IgniteRunnable {

    /**
     * Spring-managed cache for tracking processed evictions, injected by Ignite.
     * Marked as transient to prevent serialization (injection happens on each node).
     */
    @SpringResource(resourceClass = ProcessedEvictionsCache.class)
    private transient ProcessedEvictionsCache processedEvictionsCache;

    /**
     * Spring-managed ApplicationEventPublisher injected by Ignite.
     * This bean is provided by CacheEvictionConfiguration.applicationEventPublisher().
     * Marked as transient to prevent serialization (injection happens on each node).
     */
    @SpringResource(resourceName = "applicationEventPublisher")
    private transient ApplicationEventPublisher eventPublisher;

    /**
     * Spring-managed StructureService injected by Ignite.
     * Marked as transient to prevent serialization (injection happens on each node).
     * Available for future use if needed.
     */
    @SpringResource(resourceClass = StructureService.class)
    private transient StructureService structureService;

    /**
     * Spring-managed NamedQueriesService injected by Ignite.
     * Marked as transient to prevent serialization (injection happens on each node).
     * Available for future use if needed.
     */
    @SpringResource(resourceClass = NamedQueriesService.class)
    private transient NamedQueriesService namedQueriesService;


    /**
     * Properties injected by Ignite during construction on external nodes. 
     */
    private final EvictionSourceType evictionSourceType; // "STRUCTURE" or "NAMED_QUERY"
    private final EvictionSourceOperation evictionOperation; // "MODIFY" or "DELETE"
    private final String applicationId;
    private final String structureId;
    private final String namedQueryId;
    private final long timestamp; // Timestamp to prevent duplicate processing

    @Override
    public void run() {
            
        // Create unique key for this eviction request
        String evictionKey = "";
        if(namedQueryId != null){
            evictionKey = evictionSourceType + ":" + evictionOperation + ":" + applicationId + ":" + structureId + ":" + namedQueryId + ":" + timestamp;
        } else {
            evictionKey = evictionSourceType + ":" + evictionOperation + ":" + applicationId + ":" + structureId + ":" + timestamp;
        }

        try {
            // Verify Spring resource injection is working
            if (eventPublisher == null) {
                throw new IllegalStateException("ApplicationEventPublisher was not injected by Spring. " +
                        "Ensure Ignite is started with IgniteSpring.start() and Spring ApplicationContext is available.");
            }
            if (processedEvictionsCache == null) {
                throw new IllegalStateException("ProcessedEvictionsCache was not injected by Spring. " +
                        "Ensure Ignite is started with IgniteSpring.start() and Spring ApplicationContext is available.");
            }
            
            // Check if this eviction has already been processed
            Long existingTimestamp = processedEvictionsCache.getIfPresent(evictionKey);
            if (existingTimestamp != null && existingTimestamp.equals(timestamp)) {
                log.debug("Cache eviction already processed for key: {} (timestamp: {})", evictionKey, timestamp);
                return; // Skip duplicate processing
            }
            
            if (EvictionSourceType.STRUCTURE == evictionSourceType) {
                log.debug("Executing Structure cache eviction for key: {} (timestamp: {})", evictionKey, timestamp);
                
                if (structureId != null) {

                    if(evictionOperation == EvictionSourceOperation.MODIFY){
                        eventPublisher.publishEvent(CacheEvictionEvent.clusterModifiedStructure(applicationId, structureId));
                    } else if(evictionOperation == EvictionSourceOperation.DELETE){
                        eventPublisher.publishEvent(CacheEvictionEvent.clusterDeletedStructure(applicationId, structureId));
                    } else {
                        throw new IllegalArgumentException("Invalid eviction operation for key: " + evictionKey);
                    }
                    
                    // Mark as processed
                    processedEvictionsCache.put(evictionKey, timestamp);
                    log.debug("Successfully processed Structure cache eviction for key: {} (timestamp: {})", evictionKey, timestamp);
                } else {
                    log.warn("Structure not found for cache eviction: {} {}", applicationId, structureId);
                    throw new RuntimeException("Structure for eviction key: " + evictionKey + " not found");
                }
                
            } else if (EvictionSourceType.NAMED_QUERY == evictionSourceType) {
                log.debug("Executing NamedQuery cache eviction for key: {} (timestamp: {})", evictionKey, timestamp);
                
                if (namedQueryId != null) {

                    if(evictionOperation == EvictionSourceOperation.MODIFY){
                        eventPublisher.publishEvent(CacheEvictionEvent.clusterModifiedNamedQuery(applicationId, structureId, namedQueryId));
                    } else if(evictionOperation == EvictionSourceOperation.DELETE){
                        eventPublisher.publishEvent(CacheEvictionEvent.clusterDeletedNamedQuery(applicationId, structureId, namedQueryId));
                    } else {
                        throw new IllegalArgumentException("Invalid eviction operation: " + evictionOperation);
                    }
                    
                    // Mark as processed
                    processedEvictionsCache.put(evictionKey, timestamp);
                    log.debug("Successfully processed NamedQuery cache eviction for key: {} (timestamp: {})", evictionKey, timestamp);
                } else {
                    log.warn("NamedQuery not found for cache eviction: {}", evictionKey);
                    throw new RuntimeException("NamedQuery not found for eviction key: " + evictionKey);
                }
            } else {
                throw new IllegalArgumentException("Invalid eviction type: " + evictionSourceType);
            }

        } catch (Exception e) {
            String message = String.format("Cache eviction failed for cluster key for {} (timestamp: {})", evictionKey, timestamp);
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }
}
