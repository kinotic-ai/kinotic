package org.kinotic.structures.auth.api.services;

import org.kinotic.structures.auth.internal.services.DefaultCaffeineCacheFactory.CacheBuilder;

public interface CaffeineCacheFactory {
    <K, V> CacheBuilder<K, V> newBuilder();
}
