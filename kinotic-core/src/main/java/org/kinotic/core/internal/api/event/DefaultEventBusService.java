


package org.kinotic.core.internal.api.event;

import io.vertx.core.Context;
import io.vertx.core.Future;
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
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventBusService;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.EventConsumer;
import org.kinotic.core.api.event.ListenerStatus;
import org.kinotic.core.internal.config.IgniteCacheConstants;
import org.kinotic.core.internal.api.aignite.SubscriptionInfoCacheEntryEventFilter;
import org.kinotic.core.internal.api.aignite.SubscriptionInfoCacheEntryListener;
import org.kinotic.core.internal.utils.IgniteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link EventBusService} using the vertx {@link io.vertx.core.eventbus.EventBus} as a backend
 *
 *
 * Created by navid on 11/5/19
 */
@Component
public class DefaultEventBusService implements EventBusService {

    private static final Logger log = LoggerFactory.getLogger(DefaultEventBusService.class);
    private final Ignite ignite;
    private final ClusterManager clusterManager;
    private final Vertx vertx;
    private final Scheduler scheduler;
    // Addresses this node currently has a local consumer for, reference counted. Populated only by
    // listen() on this node, so it holds purely local registrations and lets sends prefer local delivery.
    private final Map<String, Integer> localListenerCounts = new ConcurrentHashMap<>();
    // This is the cache used by the IgniteVertxCluster manager to track subscriptions
    private IgniteCache<String, Set<IgniteRegistrationInfo>> subscriptionsCache;

    public DefaultEventBusService(@Autowired(required = false)
                                  ClusterManager clusterManager,
                                  @Autowired(required = false)
                                  Ignite ignite,
                                  Vertx vertx) {

        this.clusterManager = clusterManager;
        this.ignite = ignite;
        this.vertx = vertx;

        scheduler = Schedulers.fromExecutor(command -> vertx.executeBlocking(() -> {
            command.run();
            return null;
        }));

        if(ignite != null) {
            subscriptionsCache = ignite.cache("__vertx.subs");
        }
    }

    @Override
    public Future<Boolean> isAnybodyListening(CRI cri) {
        if(ignite == null){
            throw new IllegalStateException("This method is not available when ignite is disabled");
        }
        return IgniteUtil.futureToVertxFuture(() -> subscriptionsCache.containsKeyAsync(cri.baseResource()));
    }

    @Override
    public EventConsumer listen(CRI cri) {
        Validate.notNull(cri, "The cri must be provided");
        String address = cri.baseResource();
        MessageConsumer<Event<byte[]>> consumer = vertx.eventBus().consumer(address);
        localListenerCounts.merge(address, 1, Integer::sum);
        return new DefaultEventConsumer(consumer,
                                        () -> localListenerCounts.computeIfPresent(address, (k, count) -> count == 1 ? null : count - 1));
    }

    @Override
    public Flux<ListenerStatus> monitorListenerStatus(CRI cri) {
        if(ignite == null){
            throw new IllegalStateException("This method is not available when ignite is disabled");
        }
        String address = cri.baseResource();
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
                    FactoryBuilder.factoryOf(new SubscriptionInfoCacheEntryEventFilter(address));

            MutableCacheEntryListenerConfiguration<IgniteRegistrationInfo, Boolean> cacheEntryListenerConfiguration =
                    new MutableCacheEntryListenerConfiguration<>(listenerFactory, filterFactory, false, false);

            sink.onDispose(() -> {
                log.trace("Disposing of monitorListenerStatus for cri: {}", address);
                vertxContext.executeBlocking(() -> {
                    cache.deregisterCacheEntryListener(cacheEntryListenerConfiguration);
                    return null;
                });
            });

            cache.registerCacheEntryListener(cacheEntryListenerConfiguration);

            // Make sure we didn't miss a subscription ending while we were setting up the listener
            Promise<List<RegistrationInfo>> promise = Promise.promise();
            clusterManager.getRegistrations(address, promise);

            promise.future().onComplete(ar -> {
                if(ar.succeeded()){
                    List<RegistrationInfo> list = ar.result();
                    if(list != null && !list.isEmpty()){
                        vertxContext.executeBlocking(() -> sink.next(ListenerStatus.ACTIVE));
                    }else{
                        vertxContext.executeBlocking(() -> sink.next(ListenerStatus.INACTIVE));
                    }
                } else {
                    log.trace("Failed getting subscriptions for monitorListenerStatus for cri: {}", address);
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
        String baseResource = event.cri().baseResource();
        DeliveryOptions deliveryOptions = createDeliveryOptions(event, baseResource);
        vertx.eventBus().send(baseResource,
                              event,
                              deliveryOptions);
    }

    @Override
    public Future<Void> sendWithAck(Event<byte[]> event) {
        Validate.notNull(event, "Event must not be null");
        String baseResource = event.cri().baseResource();
        DeliveryOptions deliveryOptions = createDeliveryOptions(event, baseResource);
        return vertx.eventBus()
                    .request(baseResource,
                             event,
                             deliveryOptions)
                    .mapEmpty();
    }

    DeliveryOptions createDeliveryOptions(Event<?> event, String baseResource){
        DeliveryOptions deliveryOptions = new DeliveryOptions();
        deliveryOptions.setTracingPolicy(TracingPolicy.IGNORE);
        // When this node already hosts the target service, pin the request to the local handler rather
        // than letting Vert.x round-robin to a remote node that would proxy the same backend.
        if(EventConstants.SERVICE_DESTINATION_SCHEME.equals(event.cri().scheme())
               && localListenerCounts.containsKey(baseResource)){
            deliveryOptions.setLocalOnly(true);
        }
        return deliveryOptions;
    }

}
