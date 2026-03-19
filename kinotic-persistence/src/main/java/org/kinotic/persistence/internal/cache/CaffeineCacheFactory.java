package org.kinotic.persistence.internal.cache;

import org.kinotic.persistence.internal.cache.DefaultCaffeineCacheFactory.CacheBuilder;

public interface CaffeineCacheFactory {
    <K, V> CacheBuilder<K, V> newBuilder();
}
