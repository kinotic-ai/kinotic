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

package org.kinotic.rpc.internal.core.api.event;

import io.vertx.core.Context;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.spi.cluster.ignite.impl.IgniteRegistrationInfo;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.Validate;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.kinotic.rpc.api.event.Event;
import org.kinotic.rpc.api.event.EventBusService;
import org.kinotic.rpc.api.event.EventConstants;
import org.kinotic.rpc.api.event.ListenerStatus;
import org.kinotic.rpc.internal.config.IgniteCacheConstants;
import org.kinotic.rpc.internal.core.api.aignite.SubscriptionInfoCacheEntryEventFilter;
import org.kinotic.rpc.internal.core.api.aignite.SubscriptionInfoCacheEntryListener;
import org.kinotic.rpc.internal.utils.IgniteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import javax.cache.configuration.Factory;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryListener;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation of {@link EventBusService} using the vertx {@link io.vertx.core.eventbus.EventBus} as a backend
 *
 *
 * Created by navid on 11/5/19
 */
@Component
public class DefaultEventBusService implements EventBusService {

    private static final Logger log = LoggerFactory.getLogger(DefaultEventBusService.class);
    @Autowired(required = false) // this done so unit tests can complete faster. Kinda silly but hey that is unit tests.. I guess I could mock..
    private Ignite ignite;
    @Autowired(required = false)
    private ClusterManager clusterManager;
    private Scheduler scheduler;
    // This is the cache used by the IgniteVertxCluster manager to track subscriptions
    private IgniteCache<String, Set<IgniteRegistrationInfo>> subscriptionsCache;
    @Autowired
    private Vertx vertx;

    @PostConstruct
    public void init(){
        scheduler = Schedulers.fromExecutor(command -> vertx.executeBlocking(() -> {
            command.run();
            return null;
        }));

        if(ignite != null) {
            subscriptionsCache = ignite.cache("__vertx.subs");
        }

    }

    @Override
    public Mono<Boolean> isAnybodyListening(String cri) {
        if(ignite == null){
            throw new IllegalStateException("This method is not available when ignite is disabled");
        }
        return IgniteUtil.futureToMono(() -> subscriptionsCache.containsKeyAsync(cri));
    }

    @Override
    public Flux<Event<byte[]>> listen(String cri) {
        Validate.notEmpty(cri, "The cri must be provided");

        return _listen(cri, null);
    }

    @Override
    public Mono<Flux<Event<byte[]>>> listenWithAck(String cri) {
        Validate.notEmpty(cri, "The cri must be provided");

        return Mono.create(sink -> {
            final MessageConsumer<byte[]> consumer = vertx.eventBus().consumer(cri);
            final ConnectableFlux<Event<byte[]>> flux = _listen(null, consumer).publish();
            consumer.completion().onComplete(ar ->{
                if(ar.succeeded()){
                    sink.success(flux);
                }else{
                    sink.error(ar.cause());
                }
            });
            flux.connect(); // we have to connect now so flux create will be signaled and vertx consumer handler will be set
        });
    }

    @Override
    public Flux<ListenerStatus> monitorListenerStatus(String cri) {
        if(ignite == null){
            throw new IllegalStateException("This method is not available when ignite is disabled");
        }
        Flux<ListenerStatus> ret = Flux.create(sink -> {

            Context vertxContext = vertx.getOrCreateContext();

            IgniteCache<IgniteRegistrationInfo, Boolean> cache = ignite.cache(IgniteCacheConstants.VERTX_SUBSCRIPTION_CACHE);

            if(cache == null) {
                sink.error(new IllegalStateException("The vertx subscription cache is not available"));
                return;
            }

            Factory<? extends CacheEntryListener<IgniteRegistrationInfo, Boolean>> listenerFactory =
                    FactoryBuilder.factoryOf(new SubscriptionInfoCacheEntryListener(sink, vertxContext));

            Factory<? extends CacheEntryEventFilter<IgniteRegistrationInfo, Boolean>> filterFactory =
                    FactoryBuilder.factoryOf(new SubscriptionInfoCacheEntryEventFilter(cri));

            MutableCacheEntryListenerConfiguration<IgniteRegistrationInfo, Boolean> cacheEntryListenerConfiguration =
                    new MutableCacheEntryListenerConfiguration<>(listenerFactory, filterFactory, false, false);

            sink.onDispose(() -> {
                log.trace("Disposing of monitorListenerStatus for cri: {}", cri);
                vertxContext.executeBlocking(() -> {
                    cache.deregisterCacheEntryListener(cacheEntryListenerConfiguration);
                    return null;
                });
            });

            cache.registerCacheEntryListener(cacheEntryListenerConfiguration);

            // Make sure we didn't miss a subscription ending while we were setting up the listener
            Promise<List<RegistrationInfo>> promise = Promise.promise();
            clusterManager.getRegistrations(cri, promise);

            promise.future().onComplete(ar -> {
                if(ar.succeeded()){
                    List<RegistrationInfo> list = ar.result();
                    if(list != null && !list.isEmpty()){
                        vertxContext.executeBlocking(() -> sink.next(ListenerStatus.ACTIVE));
                    }else{
                        vertxContext.executeBlocking(() -> sink.next(ListenerStatus.INACTIVE));
                    }
                } else {
                    log.trace("Failed getting subscriptions for monitorListenerStatus for cri: {}", cri);
                    vertxContext.executeBlocking(() -> {
                        sink.error(ar.cause());
                        return null;
                    });

                }
            });
        });
        return ret.subscribeOn(scheduler);
    }

    @Override
    public void send(Event<byte[]> event) {
        DeliveryOptions deliveryOptions = createDeliveryOptions(event);
        vertx.eventBus().send(event.cri().baseResource(),
                              event.data(),
                              deliveryOptions);
    }

    @Override
    public Mono<Void> sendWithAck(Event<byte[]> event) {
        Validate.notNull(event, "Event must not be null");
        return Mono.create(sink -> {
            DeliveryOptions deliveryOptions = createDeliveryOptions(event);
            // We expect that a response will be sent upon receipt. This will happen automatically if the listener is created with this class.
            vertx.eventBus()
                 .request(event.cri().baseResource(),
                          event.data(),
                          deliveryOptions)
                 .onComplete(reply -> {
                     if(reply.succeeded()){
                         sink.success();
                     }else{
                         sink.error(reply.cause());
                     }
                 });
        }).subscribeOn(scheduler).then();
    }

    private Flux<Event<byte[]>> _listen(String cri, MessageConsumer<byte[]> vertxEventBusConsumer) {
        MessageConsumer<byte[]> consumer;
        if(vertxEventBusConsumer != null){
            consumer = vertxEventBusConsumer;
        }else{
            consumer = vertx.eventBus().consumer(cri);
        }

        Flux<Event<byte[]>> ret = Flux.create(fluxSink -> {
            // Setup all required handlers that are needed prior to consuming messages
            fluxSink.onDispose(consumer::unregister);

            // TODO: deal with back pressure properly.. ?
            //fluxSink.onRequest()

            consumer.exceptionHandler(fluxSink::error);
            consumer.endHandler(event -> fluxSink.complete()); // this should never occur, but we handle in case..

            // now activate handler to start consuming messages
            consumer.handler(message -> {
                // ack that we received the message if desired by sender.
                if (message.replyAddress() != null){
                    message.reply(null);
                }

                if(!fluxSink.isCancelled()) {
                    vertx.executeBlocking(() -> fluxSink.next(new MessageEventAdapter<>(message)));
                }
            });
        });

        return ret; // ensure message delivery happens on vertx event loop, not sure but this by itself did not move the next above to the work loop
    }

    private DeliveryOptions createDeliveryOptions(Event<?> event){
        DeliveryOptions deliveryOptions = new DeliveryOptions();
        deliveryOptions.setTracingPolicy(TracingPolicy.IGNORE);
        // fast path for MultiMapMetadataAdapter's
        if(event.metadata() instanceof MultiMapMetadataAdapter){
            deliveryOptions.setHeaders(((MultiMapMetadataAdapter)event.metadata()).getMultiMap());
        }else{
            for(Map.Entry<String, String> entry: event.metadata()){
                deliveryOptions.addHeader(entry.getKey(), entry.getValue());
            }
        }
        deliveryOptions.addHeader(EventConstants.CRI_HEADER, event.cri().raw());
        return deliveryOptions;
    }

}
