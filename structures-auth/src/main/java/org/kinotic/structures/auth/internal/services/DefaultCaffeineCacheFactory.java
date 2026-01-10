package org.kinotic.structures.auth.internal.services;

import com.github.benmanes.caffeine.cache.*;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.structures.auth.api.domain.EvictionEvent;
import org.kinotic.structures.auth.api.services.CaffeineCacheFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Factory class for creating Caffeine caches with a fluent builder API.
 * Centralizes cache creation to allow consistent configuration and optional
 * per-cache removal listeners for tracking evictions.
 * <p>
 * Created by Nic Padilla on 1/2/26.
 */
@Slf4j
@Component
public class DefaultCaffeineCacheFactory implements CaffeineCacheFactory {

    private final Optional<EvictionEventRecorder> evictionRecorder;

    public DefaultCaffeineCacheFactory(Optional<EvictionEventRecorder> evictionRecorder) {
        this.evictionRecorder = evictionRecorder;
        if (evictionRecorder.isPresent()) {
            log.info("CaffeineCacheFactory initialized with eviction event recorder");
        }
    }

    /**
     * Creates a new cache builder with generic key and value types.
     *
     * @param <K> the type of keys maintained by the cache
     * @param <V> the type of mapped values
     * @return a new CacheBuilder instance
     */
    public <K, V> CacheBuilder<K, V> newBuilder() {
        return new CacheBuilder<>(evictionRecorder);
    }

    /**
     * Fluent builder for creating Caffeine caches with centralized configuration.
     *
     * @param <K> the type of keys maintained by the cache
     * @param <V> the type of mapped values
     */
    public static class CacheBuilder<K, V> {

        private final Optional<EvictionEventRecorder> evictionRecorder;
        private String name;
        private Duration expireAfterAccess;
        private Duration expireAfterWrite;
        private long maximumSize = -1;
        private RemovalListener<K, V> removalListener;
        private RemovalListener<K, V> evictionListener;
        private Executor executor;

        CacheBuilder(Optional<EvictionEventRecorder> evictionRecorder) {
            this.evictionRecorder = evictionRecorder;
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
         * Documentation from Caffeine:
         * 
         * Specifies a listener instance that caches should notify each time an entry is removed for any
         * {@linkplain RemovalCause reason}. The cache will invoke this listener on the configured
         * {@link #executor(Executor)} after the entry's removal operation has completed. In the case of
         * expiration or reference collection, the entry may be pending removal and will be discarded as
         * part of the routine maintenance described in the class documentation above. For a more prompt
         * notification on expiration a {@link #scheduler(Scheduler)} may be configured. An
         * {@link #evictionListener(RemovalListener)} may be preferred when the listener should be invoked
         * as part of the atomic operation to remove the entry.
         * <p>
         * <b>Important note:</b> after invoking this method, do not continue to use <i>this</i> cache
         * builder reference; instead use the reference this method <i>returns</i>. At runtime, these
         * point to the same instance, but only the returned reference has the correct generic type
         * information so as to ensure type safety. For best results, use the standard method-chaining
         * idiom illustrated in the class documentation above, configuring a builder and building your
         * cache in a single statement. Failure to heed this advice can result in a
         * {@link ClassCastException} being thrown by a cache operation at some <i>undefined</i> point in
         * the future.
         * <p>
         * <b>Warning:</b> any exception thrown by {@code listener} will <i>not</i> be propagated to the
         * {@code Cache} user, only logged via a {@link Logger}.
         *
         * @param removalListener the listener to notify when entries are removed
         * @return this builder instance
         */
        public CacheBuilder<K, V> removalListener(RemovalListener<K, V> removalListener) {
            this.removalListener = removalListener;
            return this;
        }

        /**
         * Documentation from Caffeine:
         * 
         * Specifies a listener instance that caches should notify each time an entry is evicted. The
         * cache will invoke this listener during the atomic operation to remove the entry. In the case of
         * expiration or reference collection, the entry may be pending removal and will be discarded as
         * part of the routine maintenance described in the class documentation above. For a more prompt
         * notification on expiration a {@link #scheduler(Scheduler)} may be configured. A
         * {@link #removalListener(RemovalListener)} may be preferred when the listener should be invoked
         * for any {@linkplain RemovalCause reason}, be performed outside of the atomic operation to
         * remove the entry, or be delegated to the configured {@link #executor(Executor)}.
         * <p>
         * <b>Important note:</b> after invoking this method, do not continue to use <i>this</i> cache
         * builder reference; instead use the reference this method <i>returns</i>. At runtime, these
         * point to the same instance, but only the returned reference has the correct generic type
         * information so as to ensure type safety. For best results, use the standard method-chaining
         * idiom illustrated in the class documentation above, configuring a builder and building your
         * cache in a single statement. Failure to heed this advice can result in a
         * {@link ClassCastException} being thrown by a cache operation at some <i>undefined</i> point in
         * the future.
         * <p>
         * <b>Warning:</b> any exception thrown by {@code listener} will <i>not</i> be propagated to the
         * {@code Cache} user, only logged via a {@link Logger}.
         * <p>
         * This feature cannot be used in conjunction when {@link #weakKeys()} is combined with
         * {@link #buildAsync}.
         * @param removalListener the listener to notify when entries are removed
         * @return this builder instance
         */
        public CacheBuilder<K, V> evictionListener(RemovalListener<K, V> evictionListener) {
            this.evictionListener = evictionListener;
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
                caffeine = caffeine.expireAfterAccess(expireAfterAccess);
            }

            if (expireAfterWrite != null) {
                caffeine = caffeine.expireAfterWrite(expireAfterWrite);
            }

            if (maximumSize >= 0) {
                caffeine = caffeine.maximumSize(maximumSize);
            }

            if (executor != null) {
                caffeine = caffeine.executor(executor);
            }

            if(evictionListener != null) {
                caffeine = caffeine.evictionListener(evictionListener);
            }

            caffeine = caffeine.removalListener((RemovalListener<K, V>) (key, value, cause) -> {
                if(log.isTraceEnabled()) {
                    log.trace("Removal listener called for cache: {}, key: {}, value: {}, cause: {}", name, key, value, cause);
                }
                // Record eviction event to CSV if recorder is present
                evictionRecorder.ifPresent(recorder -> 
                    recorder.record(new EvictionEvent(
                        name != null ? name : "unnamed",
                        String.valueOf(key),
                        String.valueOf(value),
                        cause,
                        System.currentTimeMillis()
                    ))
                );

                // Call user-provided eviction listener if present
                if (removalListener != null) {
                    removalListener.onRemoval(key, value, cause);
                }
            });
            
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
