package org.kinotic.persistence.internal.cache.events;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

import org.kinotic.persistence.api.model.EntityDefinition;
import org.springframework.context.ApplicationEvent;

/**
 * Cache eviction event specifically for caches that need to be evicted
 * This is used when a {@link EntityDefinition} or named query is updated and related caches need to be evicted
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class CacheEvictionEvent extends ApplicationEvent {

    private final EvictionSourceType evictionSourceType;
    private final CacheEvictionSource evictionSource;
    private final EvictionSourceOperation evictionOperation;
    private final Instant eventTimestamp;

    /**
     * ID of the {@link EntityDefinition} that is being evicted
     */
    private final String entityDefinitionId;
    /**
     * ID of the named query that is being evicted, if any
     */
    private final String namedQueryId;
    /**
     * Application id of the {@link EntityDefinition} or associated named query that is being evicted
     */
    private final String applicationId;

    public CacheEvictionEvent(CacheEvictionSource evictionSource, EvictionSourceType evictionSourceType, EvictionSourceOperation evictionOperation, String applicationId, String entityDefinitionId, String namedQueryId) {
        super(evictionSource); // Use evictionSource as the Spring event source
        this.evictionSource = evictionSource;
        this.evictionSourceType = evictionSourceType;
        this.evictionOperation = evictionOperation;
        this.eventTimestamp = Instant.now();
        this.applicationId = applicationId;
        this.entityDefinitionId = entityDefinitionId;
        this.namedQueryId = namedQueryId;
    }

    public static CacheEvictionEvent localModifiedNamedQuery(String applicationId, String entityDefinitionId, String namedQueryId) {
        return new CacheEvictionEvent(CacheEvictionSource.LOCAL_MESSAGE, EvictionSourceType.NAMED_QUERY, EvictionSourceOperation.MODIFY, applicationId, entityDefinitionId, namedQueryId);
    }

    public static CacheEvictionEvent localDeletedNamedQuery(String applicationId, String entityDefinitionId, String namedQueryId) {
        return new CacheEvictionEvent(CacheEvictionSource.LOCAL_MESSAGE, EvictionSourceType.NAMED_QUERY, EvictionSourceOperation.DELETE, applicationId, entityDefinitionId, namedQueryId);
    }

    public static CacheEvictionEvent localModifiedEntityDefinition(String applicationId, String entityDefinitionId) {
        return new CacheEvictionEvent(CacheEvictionSource.LOCAL_MESSAGE, EvictionSourceType.ENTITY_DEFINITION, EvictionSourceOperation.MODIFY, applicationId, entityDefinitionId, null);
    }

    public static CacheEvictionEvent localDeletedEntityDefinition(String applicationId, String entityDefinitionId) {
        return new CacheEvictionEvent(CacheEvictionSource.LOCAL_MESSAGE, EvictionSourceType.ENTITY_DEFINITION, EvictionSourceOperation.DELETE, applicationId, entityDefinitionId, null);
    }


    

    public static CacheEvictionEvent clusterModifiedNamedQuery(String applicationId, String entityDefinitionId, String namedQueryId) {
        return new CacheEvictionEvent(CacheEvictionSource.CLUSTER_MESSAGE, EvictionSourceType.NAMED_QUERY, EvictionSourceOperation.MODIFY, applicationId, entityDefinitionId, namedQueryId);
    }

    public static CacheEvictionEvent clusterDeletedNamedQuery(String applicationId, String entityDefinitionId, String namedQueryId) {
        return new CacheEvictionEvent(CacheEvictionSource.CLUSTER_MESSAGE, EvictionSourceType.NAMED_QUERY, EvictionSourceOperation.DELETE, applicationId, entityDefinitionId, namedQueryId);
    }

    public static CacheEvictionEvent clusterModifiedEntityDefinition(String applicationId, String entityDefinitionId) {
        return new CacheEvictionEvent(CacheEvictionSource.CLUSTER_MESSAGE, EvictionSourceType.ENTITY_DEFINITION, EvictionSourceOperation.MODIFY, applicationId, entityDefinitionId, null);
    }

    public static CacheEvictionEvent clusterDeletedEntityDefinition(String applicationId, String entityDefinitionId) {
        return new CacheEvictionEvent(CacheEvictionSource.CLUSTER_MESSAGE, EvictionSourceType.ENTITY_DEFINITION, EvictionSourceOperation.DELETE, applicationId, entityDefinitionId, null);
    }
}
