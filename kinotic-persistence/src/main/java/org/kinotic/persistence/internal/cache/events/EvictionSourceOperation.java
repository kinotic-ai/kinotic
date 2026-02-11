package org.kinotic.persistence.internal.cache.events;

import lombok.Getter;

/**
 * Type-safe enum defining the source/trigger of cache eviction events
 * This helps identify what initiated the cache eviction and helps determine
 * the appropriate execution path
 */
@Getter
public enum EvictionSourceOperation {

    /**
     * Cache eviction triggered by a modify operation
     */
    MODIFY("Modify"),
    
    /**
     * Cache eviction triggered by a delete operation
     */
    DELETE("Delete");

    private final String displayName;

    EvictionSourceOperation(String displayName) {
        this.displayName = displayName;
    }

}
