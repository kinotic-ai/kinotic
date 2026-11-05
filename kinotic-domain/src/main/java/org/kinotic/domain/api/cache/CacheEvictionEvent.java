package org.kinotic.domain.api.cache;

import lombok.Data;
import org.kinotic.core.api.ServerType;
import org.kinotic.core.api.event.ClusterEvent;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.persistence.EntityDefinition;

import java.util.Set;

/**
 * Cache eviction event specifically for caches that need to be evicted
 * This is used when a {@link EntityDefinition} or named query is updated and related caches need to be evicted.
 * It is delivered to the app servers, where the entity services that hold the caches run.
 */
@Data
public class CacheEvictionEvent implements ClusterEvent {

    private final EvictionSourceType evictionSourceType;

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

    /** The eviction of a named query that changed or was deleted. */
    public static CacheEvictionEvent namedQuery(ApplicationKey applicationKey, String entityDefinitionId, String namedQueryId) {
        return new CacheEvictionEvent(EvictionSourceType.NAMED_QUERY, entityDefinitionId, namedQueryId, applicationKey);
    }

    /** The eviction of an entity definition that changed or was deleted. */
    public static CacheEvictionEvent entityDefinition(ApplicationKey applicationKey, String entityDefinitionId) {
        return new CacheEvictionEvent(EvictionSourceType.ENTITY_DEFINITION, entityDefinitionId, null, applicationKey);
    }

    @Override
    public Set<ServerType> serverTypeFilter() {
        return Set.of(ServerType.APP);
    }
}
