package org.kinotic.structures.api.cache;

import com.github.benmanes.caffeine.cache.*;
import com.github.benmanes.caffeine.cache.Caffeine;


import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * Factory class for creating Caffeine caches with a fluent builder API.
 * Centralizes cache creation to allow consistent configuration and optional
 * per-cache removal listeners for tracking evictions.
 * <p>
 * Created by Navíd Mitchell 🤪 on 1/2/26.
 */
public final class CaffeineCacheFactory {

    private CaffeineCacheFactory() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates a new cache builder with generic key and value types.
     *
     * @param <K> the type of keys maintained by the cache
     * @param <V> the type of mapped values
     * @return a new CacheBuilder instance
     */
    public static <K, V> CacheBuilder<K, V> newBuilder() {
        return new CacheBuilder<>();
    }

    /**
     * Fluent builder for creating Caffeine caches with centralized configuration.
     *
     * @param <K> the type of keys maintained by the cache
     * @param <V> the type of mapped values
     */
    public static class CacheBuilder<K, V> {

        private String name;
        private Duration expireAfterAccess;
        private Duration expireAfterWrite;
        private long maximumSize = -1;
        private RemovalListener<K, V> removalListener;
        private Executor executor;

        CacheBuilder() {
            // Package-private constructor
        }

        /**
         * Sets the name of the cache for identification in logs and metrics.
         *
         * @param name the cache name
         * @return this builder instance
         */
        public CacheBuilder<K, V> name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Specifies that each entry should be automatically removed from the cache
         * once a fixed duration has elapsed after the entry's creation, the most
         * recent replacement of its value, or its last access.
         *
         * @param duration the duration after which an entry should be removed
         * @return this builder instance
         */
        public CacheBuilder<K, V> expireAfterAccess(Duration duration) {
            this.expireAfterAccess = duration;
            return this;
        }

        /**
         * Specifies that each entry should be automatically removed from the cache
         * once a fixed duration has elapsed after the entry's creation or the most
         * recent replacement of its value.
         *
         * @param duration the duration after which an entry should be removed
         * @return this builder instance
         */
        public CacheBuilder<K, V> expireAfterWrite(Duration duration) {
            this.expireAfterWrite = duration;
            return this;
        }

        /**
         * Specifies the maximum number of entries the cache may contain.
         *
         * @param maximumSize the maximum size of the cache
         * @return this builder instance
         */
        public CacheBuilder<K, V> maximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
            return this;
        }

        /**
         * Specifies a listener instance that caches should notify each time an entry
         * is removed for any reason. The listener will be invoked on the executor
         * if one is specified, otherwise on the common ForkJoinPool.
         *
         * @param removalListener the listener to notify when entries are removed
         * @return this builder instance
         */
        public CacheBuilder<K, V> removalListener(RemovalListener<K, V> removalListener) {
            this.removalListener = removalListener;
            return this;
        }

        /**
         * Specifies the executor to use for asynchronous operations including
         * removal listener notifications.
         *
         * @param executor the executor for async operations
         * @return this builder instance
         */
        public CacheBuilder<K, V> executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Builds a synchronous cache with the configured settings.
         *
         * @return a new Cache instance
         */
        public Cache<K, V> build() {
            return configureCaffeine().build();
        }

        /**
         * Builds an asynchronous cache without a loader.
         *
         * @return a new AsyncCache instance
         */
        public AsyncCache<K, V> buildAsync() {
            return configureCaffeine().buildAsync();
        }

        /**
         * Builds an asynchronous loading cache with the specified loader.
         *
         * @param loader the cache loader used to obtain new values
         * @return a new AsyncLoadingCache instance
         */
        public AsyncLoadingCache<K, V> buildAsync(AsyncCacheLoader<K, V> loader) {
            return configureCaffeine().buildAsync(loader);
        }

        private Caffeine<K, V> configureCaffeine() {
            @SuppressWarnings("unchecked")
            Caffeine<K, V> caffeine = (Caffeine<K, V>) Caffeine.newBuilder();

            if (expireAfterAccess != null) {
                caffeine.expireAfterAccess(expireAfterAccess);
            }

            if (expireAfterWrite != null) {
                caffeine.expireAfterWrite(expireAfterWrite);
            }

            if (maximumSize >= 0) {
                caffeine.maximumSize(maximumSize);
            }

            if (executor != null) {
                caffeine.executor(executor);
            }

            if (removalListener != null) {
                caffeine.removalListener(removalListener);
            }

            return caffeine;
        }

        /**
         * Gets the configured name for this cache.
         *
         * @return the cache name, or null if not set
         */
        public String getName() {
            return name;
        }
    }
}

