package org.kinotic.domain.internal.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.resources.SpringApplicationContextResource;
import org.kinotic.domain.api.cache.CacheEvictionEvent;
import org.kinotic.domain.api.cache.EvictionSourceOperation;
import org.kinotic.domain.api.cache.EvictionSourceType;
import org.springframework.context.ApplicationContext;

/**
 * Simple Ignite Compute Grid task for cluster-wide cache eviction.
 * Uses IDs to avoid serialization issues.
 */
@Slf4j
@RequiredArgsConstructor
public class ClusterCacheEvictionTask implements IgniteRunnable {

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
    private final long timestamp; // Correlates the log lines of one eviction across its retries

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
