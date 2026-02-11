package org.kinotic.auth.api.services;

import org.kinotic.auth.internal.services.DefaultCaffeineCacheFactory.CacheBuilder;

public interface CaffeineCacheFactory {
    <K, V> CacheBuilder<K, V> newBuilder();
}
