package org.kinotic.persistence.internal.cache.events;

/**
 * Type-safe enum defining the source/trigger of cache eviction events
 * This helps identify what initiated the cache eviction and determines
 * the appropriate execution path (local vs cluster-wide)
 */
public enum CacheEvictionSource {

    /**
     * Cache eviction triggered by a local message
     * Scope: All caches - messages of this type will be broadcast to all nodes
     */
    LOCAL_MESSAGE("Local Message"),
    
    /**
     * Cache eviction triggered by a cluster message from another node
     * Scope: All caches - messages of this type will NOT be broadcast to all nodes
     */
    CLUSTER_MESSAGE("Cluster Message");

    private final String displayName;

    CacheEvictionSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
