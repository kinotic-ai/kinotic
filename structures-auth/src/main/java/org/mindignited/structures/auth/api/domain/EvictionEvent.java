package org.mindignited.structures.auth.api.domain;

import com.github.benmanes.caffeine.cache.RemovalCause;

/**
 * Represents a cache eviction event with full details for tracking and analysis.
 */
public record EvictionEvent(
    String cacheName,
    String key,
    String value, // not used for tracking, but included for completeness
    RemovalCause cause,
    long timestamp
) {
    /**
     * Converts this event to a CSV line format.
     *
     * @return CSV formatted string: timestamp,cacheName,key,cause
     */
    public String toCsvLine() {
        return String.format("%d,%s,%s,%s%n", 
            timestamp, cacheName, key, cause);
    }
}


