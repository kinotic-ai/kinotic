


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
    @Autowired(required = false) // this done so unit tests can complete faster. Kinda silly but hey that is unit tests.. I guess I could mock..
    private Ignite ignite;
    @Autowired(required = false)
    private ClusterManager clusterManager;
    private Scheduler scheduler;
    // This is the cache used by the IgniteVertxCluster manager to track subscriptions
    private IgniteCache<String, Set<IgniteRegistrationInfo>> subscriptionsCache;
    // Addresses this node currently has a local consumer for, reference counted. Populated only by
    // listen() on this node, so it holds purely local registrations and lets sends prefer local delivery.
    private final Map<String, Integer> localListenerCounts = new ConcurrentHashMap<>();
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
    public Future<Boolean> isAnybodyListening(String cri) {
        if(ignite == null){
            throw new IllegalStateException("This method is not available when ignite is disabled");
        }
        return IgniteUtil.futureToVertxFuture(() -> subscriptionsCache.containsKeyAsync(cri));
    }

    @Override
    public EventConsumer listen(String cri) {
        Validate.notEmpty(cri, "The cri must be provided");
        MessageConsumer<byte[]> consumer = vertx.eventBus().consumer(cri);
        localListenerCounts.merge(cri, 1, Integer::sum);
        return new DefaultEventConsumer(consumer,
                                        () -> localListenerCounts.computeIfPresent(cri, (k, count) -> count == 1 ? null : count - 1));
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
        String baseResource = event.cri().baseResource();
        DeliveryOptions deliveryOptions = createDeliveryOptions(event, baseResource);
        vertx.eventBus().send(baseResource,
                              event.data(),
                              deliveryOptions);
    }

    @Override
    public Future<Void> sendWithAck(Event<byte[]> event) {
        Validate.notNull(event, "Event must not be null");
        String baseResource = event.cri().baseResource();
        DeliveryOptions deliveryOptions = createDeliveryOptions(event, baseResource);
        return vertx.eventBus()
                    .request(baseResource,
                             event.data(),
                             deliveryOptions)
                    .mapEmpty();
    }

    private DeliveryOptions createDeliveryOptions(Event<?> event, String baseResource){
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
        // When this node already hosts the target service, pin the request to the local handler rather
        // than letting Vert.x round-robin to a remote node that would proxy the same backend.
        if(shouldDeliverLocally(event, baseResource)){
            deliveryOptions.setLocalOnly(true);
        }
        return deliveryOptions;
    }

    /**
     * Whether a send to the given address should be confined to a handler on this node.
     * @return true only when the destination is a service invocation this node currently hosts
     */
    boolean shouldDeliverLocally(Event<?> event, String baseResource){
        return EventConstants.SERVICE_DESTINATION_SCHEME.equals(event.cri().scheme())
               && localListenerCounts.containsKey(baseResource);
    }

}
