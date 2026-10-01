package org.kinotic.domain.api.cache;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
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
     * Application of the {@link EntityDefinition} or associated named query that is being evicted
     */
    private final ApplicationKey applicationKey;

    public CacheEvictionEvent(CacheEvictionSource evictionSource, EvictionSourceType evictionSourceType, EvictionSourceOperation evictionOperation, ApplicationKey applicationKey, String entityDefinitionId, String namedQueryId) {
        super(evictionSource);
        this.evictionSource = evictionSource;
        this.evictionSourceType = evictionSourceType;
        this.evictionOperation = evictionOperation;
        this.eventTimestamp = Instant.now();
        this.applicationKey = applicationKey;
        this.entityDefinitionId = entityDefinitionId;
        this.namedQueryId = namedQueryId;
    }

    public static CacheEvictionEvent localModifiedNamedQuery(ApplicationKey applicationKey, String entityDefinitionId, String namedQueryId) {
        return new CacheEvictionEvent(CacheEvictionSource.LOCAL_MESSAGE, EvictionSourceType.NAMED_QUERY, EvictionSourceOperation.MODIFY, applicationKey, entityDefinitionId, namedQueryId);
    }

    public static CacheEvictionEvent localDeletedNamedQuery(ApplicationKey applicationKey, String entityDefinitionId, String namedQueryId) {
        return new CacheEvictionEvent(CacheEvictionSource.LOCAL_MESSAGE, EvictionSourceType.NAMED_QUERY, EvictionSourceOperation.DELETE, applicationKey, entityDefinitionId, namedQueryId);
    }

    public static CacheEvictionEvent localModifiedEntityDefinition(ApplicationKey applicationKey, String entityDefinitionId) {
        return new CacheEvictionEvent(CacheEvictionSource.LOCAL_MESSAGE, EvictionSourceType.ENTITY_DEFINITION, EvictionSourceOperation.MODIFY, applicationKey, entityDefinitionId, null);
    }

    public static CacheEvictionEvent localDeletedEntityDefinition(ApplicationKey applicationKey, String entityDefinitionId) {
        return new CacheEvictionEvent(CacheEvictionSource.LOCAL_MESSAGE, EvictionSourceType.ENTITY_DEFINITION, EvictionSourceOperation.DELETE, applicationKey, entityDefinitionId, null);
    }

    public static CacheEvictionEvent clusterModifiedNamedQuery(ApplicationKey applicationKey, String entityDefinitionId, String namedQueryId) {
        return new CacheEvictionEvent(CacheEvictionSource.CLUSTER_MESSAGE, EvictionSourceType.NAMED_QUERY, EvictionSourceOperation.MODIFY, applicationKey, entityDefinitionId, namedQueryId);
    }

    public static CacheEvictionEvent clusterDeletedNamedQuery(ApplicationKey applicationKey, String entityDefinitionId, String namedQueryId) {
        return new CacheEvictionEvent(CacheEvictionSource.CLUSTER_MESSAGE, EvictionSourceType.NAMED_QUERY, EvictionSourceOperation.DELETE, applicationKey, entityDefinitionId, namedQueryId);
    }

    public static CacheEvictionEvent clusterModifiedEntityDefinition(ApplicationKey applicationKey, String entityDefinitionId) {
        return new CacheEvictionEvent(CacheEvictionSource.CLUSTER_MESSAGE, EvictionSourceType.ENTITY_DEFINITION, EvictionSourceOperation.MODIFY, applicationKey, entityDefinitionId, null);
    }

    public static CacheEvictionEvent clusterDeletedEntityDefinition(ApplicationKey applicationKey, String entityDefinitionId) {
        return new CacheEvictionEvent(CacheEvictionSource.CLUSTER_MESSAGE, EvictionSourceType.ENTITY_DEFINITION, EvictionSourceOperation.DELETE, applicationKey, entityDefinitionId, null);
    }
}
