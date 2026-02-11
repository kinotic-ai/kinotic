/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.rpc.internal.utils;

import org.kinotic.continuum.core.api.event.StreamData;
import org.kinotic.continuum.core.api.event.StreamOperation;
import org.kinotic.rpc.internal.core.api.aignite.Observer;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.Validate;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.query.ContinuousQueryWithTransformer;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.lang.IgniteClosure;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.lang.IgniteInClosure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import javax.cache.Cache;
import javax.cache.configuration.Factory;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.EventType;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 *
 * Created by Navid Mitchell on 5/29/20
 */
public class IgniteUtil {
    private static final Logger log = LoggerFactory.getLogger(IgniteUtil.class);

    public static <T> Mono<T> futureToMono(IgniteFuture<T> future){
        return Mono.create(sink ->  {
            try{
                futureToMonoSink(sink, future);
            }catch (Exception e){
                sink.error(e);
            }
        });
    }


    public static <T> Mono<T> futureToMono(Supplier<IgniteFuture<T>> futureSupplier){
        return Mono.create(sink ->  {
            try{
                futureToMonoSink(sink, futureSupplier.get());
            }catch (Exception e){
                sink.error(e);
            }
        });
    }

    public static <T> CompletableFuture<T> futureToCompletableFuture(IgniteFuture<T> igniteFuture){
        CompletableFuture<T> ret = new CompletableFuture<>();
        igniteFuture.listen((IgniteInClosure<IgniteFuture<T>>) f -> {
            try{
                ret.complete(f.get());
            }catch (Exception ex){
                ret.completeExceptionally(ex);
            }
        });
        return ret;
    }

    public static <T> void futureToMonoSink(MonoSink<T> sink, IgniteFuture<T> igniteFuture){
        igniteFuture.listen((IgniteInClosure<IgniteFuture<T>>) future -> {
            try{
                sink.success(future.get());
            }catch (Exception ex){
                sink.error(ex);
            }
        });
    }

    public static <T> Flux<T> observerToFlux(Supplier<Observer<T>> observerSupplier){
        return Flux.create(sink -> {
            Observer<T> observer = observerSupplier.get();
            observer.completionHandler(v -> sink.complete());
            observer.exceptionHandler(sink::error);
            observer.handler(sink::next);
            sink.onCancel(() -> {
                try {
                    observer.close();
                } catch (Exception e) {
                    log.error("Exception trying to close Observer",e);
                }
            });
            observer.start();
        });
    }



    public static <I,T> StreamData<I,T> cacheEntryEventToStreamData(CacheEntryEvent<? extends I,? extends T> cacheEntryEvent){
        StreamData<I,T> ret;

        StreamOperation operation = toStreamOperation(cacheEntryEvent.getEventType());
        // use the old value on remove so we can use in remote listener if desired
        if(operation == StreamOperation.REMOVE){
            ret = new StreamData<>(operation, cacheEntryEvent.getKey(), cacheEntryEvent.getOldValue());
        }else{
            ret = new StreamData<>(operation, cacheEntryEvent.getKey(), cacheEntryEvent.getValue());
        }
        return ret;
    }

    public static <I,T> StreamData<I,T> cacheEntryToStreamData(Cache.Entry<I, T> cacheEntry){
        return new StreamData<>(StreamOperation.EXISTING,
                                cacheEntry.getKey(),
                                cacheEntry.getValue());
    }

    public static StreamOperation toStreamOperation(EventType eventType){
        StreamOperation ret;
        switch (eventType){
            case CREATED:
                ret = StreamOperation.EXISTING;
                break;
            case UPDATED:
                ret = StreamOperation.UPDATE;
                break;
            case REMOVED:
            case EXPIRED:
                ret = StreamOperation.REMOVE;
                break;
            default:
                throw new IllegalArgumentException("Unknown EventType "+eventType.name());
        }
        return ret;
    }

    public static <K, V> Flux<Long> countCacheEntriesContinuous(Ignite ignite, Vertx vertx, IgniteCache<K, V> igniteCache){
        Validate.notNull(ignite, "Ignite must not be null");
        Validate.notNull(vertx, "Vertx must not be null");
        Validate.notNull(igniteCache, "The IgniteCache provided must not be null");

        Scheduler scheduler = Schedulers.fromExecutor(command -> vertx.executeBlocking(() -> {
            command.run();
            return null;
        }));

        Flux<Long> ret = Flux.create(sink -> {
            AtomicLong activeSessionsCount = new AtomicLong();

            Context vertxContext = vertx.getOrCreateContext();

            ContinuousQueryWithTransformer<K, V, Long> qry = new ContinuousQueryWithTransformer<>();
            qry.setIncludeExpired(true);

            Factory<IgniteClosure<CacheEntryEvent<? extends K, ? extends V>, Long>> transformerFactory = FactoryBuilder
                    .factoryOf(
                            (IgniteClosure<CacheEntryEvent<? extends K, ? extends V>, Long>) event  -> {
                                long change = 0;
                                if(event.getEventType() == EventType.CREATED){
                                    change = 1L;
                                }else if(event.getEventType() == EventType.REMOVED || event.getEventType() == EventType.EXPIRED){
                                    change = -1L;
                                }
                                return change;
                            });

            qry.setRemoteTransformerFactory(transformerFactory);

            qry.setRemoteFilterFactory((Factory<CacheEntryEventFilter<K, V>>)
                                               () -> event -> event.getEventType() != EventType.UPDATED);

            qry.setLocalListener(events -> vertxContext.executeBlocking(() ->{
                for(Long change  : events){
                    if(!sink.isCancelled()) {
                        sink.next(activeSessionsCount.addAndGet(change));
                    }
                }
                return null;
            }));

            // Executing the query.
            QueryCursor<Cache.Entry<K, V>> cursor = null;
            try {
                // Not sure but it seems we could lose some updates here
                long currentSize = igniteCache.sizeLong();
                activeSessionsCount.set(currentSize);
                sink.next(activeSessionsCount.get());

                cursor = igniteCache.query(qry);

                QueryCursor<Cache.Entry<K, V>> finalCursor = cursor;
                sink.onDispose(() -> safeCloseCursor(finalCursor));

            }catch (Exception e){
                safeCloseCursor(cursor);
                sink.error(e);
            }

        });

        return ret.subscribeOn(scheduler);
    }

    private static void safeCloseCursor(QueryCursor<?> cursor){
        try {
            if(cursor != null) {
                cursor.close();
            }
        } catch (Exception ex) {
            log.warn("Exception closing continuous query",ex);
        }
    }


}
