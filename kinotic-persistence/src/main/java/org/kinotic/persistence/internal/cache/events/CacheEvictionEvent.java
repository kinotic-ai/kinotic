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
     * Organization id that owns the application being evicted
     */
    private final String organizationId;
    /**
     * Application id of the {@link EntityDefinition} or associated named query that is being evicted
     */
    private final String applicationId;

    public CacheEvictionEvent(CacheEvictionSource evictionSource, EvictionSourceType evictionSourceType, EvictionSourceOperation evictionOperation, String organizationId, String applicationId, String entityDefinitionId, String namedQueryId) {
        super(evictionSource);
        this.evictionSource = evictionSource;
        this.evictionSourceType = evictionSourceType;
        this.evictionOperation = evictionOperation;
        this.eventTimestamp = Instant.now();
        this.organizationId = organizationId;
        this.applicationId = applicationId;
        this.entityDefinitionId = entityDefinitionId;
        this.namedQueryId = namedQueryId;
    }

    public static CacheEvictionEvent localModifiedNamedQuery(String organizationId, String applicationId, String entityDefinitionId, String namedQueryId) {
        return new CacheEvictionEvent(CacheEvictionSource.LOCAL_MESSAGE, EvictionSourceType.NAMED_QUERY, EvictionSourceOperation.MODIFY, organizationId, applicationId, entityDefinitionId, namedQueryId);
    }

    public static CacheEvictionEvent localDeletedNamedQuery(String organizationId, String applicationId, String entityDefinitionId, String namedQueryId) {
        return new CacheEvictionEvent(CacheEvictionSource.LOCAL_MESSAGE, EvictionSourceType.NAMED_QUERY, EvictionSourceOperation.DELETE, organizationId, applicationId, entityDefinitionId, namedQueryId);
    }

    public static CacheEvictionEvent localModifiedEntityDefinition(String organizationId, String applicationId, String entityDefinitionId) {
        return new CacheEvictionEvent(CacheEvictionSource.LOCAL_MESSAGE, EvictionSourceType.ENTITY_DEFINITION, EvictionSourceOperation.MODIFY, organizationId, applicationId, entityDefinitionId, null);
    }

    public static CacheEvictionEvent localDeletedEntityDefinition(String organizationId, String applicationId, String entityDefinitionId) {
        return new CacheEvictionEvent(CacheEvictionSource.LOCAL_MESSAGE, EvictionSourceType.ENTITY_DEFINITION, EvictionSourceOperation.DELETE, organizationId, applicationId, entityDefinitionId, null);
    }

    public static CacheEvictionEvent clusterModifiedNamedQuery(String organizationId, String applicationId, String entityDefinitionId, String namedQueryId) {
        return new CacheEvictionEvent(CacheEvictionSource.CLUSTER_MESSAGE, EvictionSourceType.NAMED_QUERY, EvictionSourceOperation.MODIFY, organizationId, applicationId, entityDefinitionId, namedQueryId);
    }

    public static CacheEvictionEvent clusterDeletedNamedQuery(String organizationId, String applicationId, String entityDefinitionId, String namedQueryId) {
        return new CacheEvictionEvent(CacheEvictionSource.CLUSTER_MESSAGE, EvictionSourceType.NAMED_QUERY, EvictionSourceOperation.DELETE, organizationId, applicationId, entityDefinitionId, namedQueryId);
    }

    public static CacheEvictionEvent clusterModifiedEntityDefinition(String organizationId, String applicationId, String entityDefinitionId) {
        return new CacheEvictionEvent(CacheEvictionSource.CLUSTER_MESSAGE, EvictionSourceType.ENTITY_DEFINITION, EvictionSourceOperation.MODIFY, organizationId, applicationId, entityDefinitionId, null);
    }

    public static CacheEvictionEvent clusterDeletedEntityDefinition(String organizationId, String applicationId, String entityDefinitionId) {
        return new CacheEvictionEvent(CacheEvictionSource.CLUSTER_MESSAGE, EvictionSourceType.ENTITY_DEFINITION, EvictionSourceOperation.DELETE, organizationId, applicationId, entityDefinitionId, null);
    }
}
