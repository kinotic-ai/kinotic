package org.kinotic.domain.api.cache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.persistence.EntityDefinition;

/**
 * Cache eviction event specifically for caches that need to be evicted
 * This is used when a {@link EntityDefinition} or named query is updated and related caches need to be evicted.
 * It is published to every node consuming it, which every node running the persistence module does, where the
 * entity services that hold the caches run.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public final class CacheEvictionEvent {

    private EvictionSourceType evictionSourceType;

    /**
     * ID of the {@link EntityDefinition} that is being evicted, null for a named query
     */
    private String entityDefinitionId;

    /**
     * ID of the named query that is being evicted, if any
     */
    private String namedQueryId;

    /**
     * Application of the {@link EntityDefinition} or associated named query that is being evicted
     */
    private ApplicationKey applicationKey;

    /** The eviction of a named query that changed or was deleted. */
    public static CacheEvictionEvent namedQuery(ApplicationKey applicationKey, String namedQueryId) {
        return new CacheEvictionEvent(EvictionSourceType.NAMED_QUERY, null, namedQueryId, applicationKey);
    }

    /** The eviction of an entity definition that changed or was deleted. */
    public static CacheEvictionEvent entityDefinition(ApplicationKey applicationKey, String entityDefinitionId) {
        return new CacheEvictionEvent(EvictionSourceType.ENTITY_DEFINITION, entityDefinitionId, null, applicationKey);
    }

}
