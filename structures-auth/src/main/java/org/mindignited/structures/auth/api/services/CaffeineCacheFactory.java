package org.mindignited.structures.auth.api.services;

import org.mindignited.structures.auth.internal.services.DefaultCaffeineCacheFactory.CacheBuilder;

public interface CaffeineCacheFactory {
    <K, V> CacheBuilder<K, V> newBuilder();
}
