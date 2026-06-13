package org.kinotic.persistence.internal.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.resources.SpringApplicationContextResource;
import org.apache.ignite.resources.SpringResource;
import org.kinotic.persistence.internal.cache.events.CacheEvictionEvent;
import org.kinotic.persistence.internal.cache.events.EvictionSourceOperation;
import org.kinotic.persistence.internal.cache.events.EvictionSourceType;
import org.springframework.context.ApplicationContext;

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
    @SpringApplicationContextResource
    private ApplicationContext eventPublisher;


    /**
     * Properties injected by Ignite during construction on external nodes. 
     */
    private final EvictionSourceType evictionSourceType; // "ENTITY_DEFINITION" or "NAMED_QUERY"
    private final EvictionSourceOperation evictionOperation; // "MODIFY" or "DELETE"
    private final String organizationId;
    private final String applicationId;
    private final String entityDefinitionId;
    private final String namedQueryId;
    private final long timestamp; // Timestamp to prevent duplicate processing

    @Override
    public void run() {
            
        // Create unique key for this eviction request
        String evictionKey = "";
        if(namedQueryId != null){
            evictionKey = evictionSourceType + ":" + evictionOperation + ":" + applicationId + ":" + entityDefinitionId + ":" + namedQueryId + ":" + timestamp;
        } else {
            evictionKey = evictionSourceType + ":" + evictionOperation + ":" + applicationId + ":" + entityDefinitionId + ":" + timestamp;
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
                log.trace("Cache eviction already processed for key: {} (timestamp: {})", evictionKey, timestamp);
                return; // Skip duplicate processing
            }
            
            if (EvictionSourceType.ENTITY_DEFINITION == evictionSourceType) {
                log.trace("Executing EntityDefinition cache eviction for key: {} (timestamp: {})", evictionKey, timestamp);
                
                if (entityDefinitionId != null) {

                    if(evictionOperation == EvictionSourceOperation.MODIFY){
                        eventPublisher.publishEvent(CacheEvictionEvent.clusterModifiedEntityDefinition(organizationId, applicationId,
                                                                                                       entityDefinitionId));
                    } else if(evictionOperation == EvictionSourceOperation.DELETE){
                        eventPublisher.publishEvent(CacheEvictionEvent.clusterDeletedEntityDefinition(organizationId, applicationId,
                                                                                                      entityDefinitionId));
                    } else {
                        throw new IllegalArgumentException("Invalid eviction operation for key: " + evictionKey);
                    }
                    
                    // Mark as processed
                    processedEvictionsCache.put(evictionKey, timestamp);
                    log.trace("Successfully processed EntityDefinition cache eviction for key: {} (timestamp: {})", evictionKey, timestamp);
                } else {
                    log.warn("EntityDefinition not found for cache eviction: {} {}", applicationId, entityDefinitionId);
                    throw new RuntimeException("EntityDefinition for eviction key: " + evictionKey + " not found");
                }
                
            } else if (EvictionSourceType.NAMED_QUERY == evictionSourceType) {
                log.trace("Executing NamedQuery cache eviction for key: {} (timestamp: {})", evictionKey, timestamp);
                
                if (namedQueryId != null) {

                    if(evictionOperation == EvictionSourceOperation.MODIFY){
                        eventPublisher.publishEvent(CacheEvictionEvent.clusterModifiedNamedQuery(organizationId, applicationId,
                                                                                                 entityDefinitionId, namedQueryId));
                    } else if(evictionOperation == EvictionSourceOperation.DELETE){
                        eventPublisher.publishEvent(CacheEvictionEvent.clusterDeletedNamedQuery(organizationId, applicationId,
                                                                                                entityDefinitionId, namedQueryId));
                    } else {
                        throw new IllegalArgumentException("Invalid eviction operation: " + evictionOperation);
                    }
                    
                    // Mark as processed
                    processedEvictionsCache.put(evictionKey, timestamp);
                    log.trace("Successfully processed NamedQuery cache eviction for key: {} (timestamp: {})", evictionKey, timestamp);
                } else {
                    log.warn("NamedQuery not found for cache eviction: {}", evictionKey);
                    throw new RuntimeException("NamedQuery not found for eviction key: " + evictionKey);
                }
            } else {
                throw new IllegalArgumentException("Invalid eviction type: " + evictionSourceType);
            }

        } catch (Exception e) {
            String message = String.format("Cache eviction failed for cluster key for %s (timestamp: %s)", evictionKey, timestamp);
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }
}
