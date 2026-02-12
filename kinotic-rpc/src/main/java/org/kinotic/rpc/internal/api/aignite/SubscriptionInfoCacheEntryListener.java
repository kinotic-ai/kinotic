

package org.kinotic.rpc.internal.api.aignite;

import java.io.Serializable;

import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.event.CacheEntryRemovedListener;

import org.kinotic.rpc.api.event.ListenerStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Context;
import io.vertx.spi.cluster.ignite.impl.IgniteRegistrationInfo;
import reactor.core.publisher.FluxSink;

/**
 * Created by 🤓 on 5/8/21.
 */
public class SubscriptionInfoCacheEntryListener implements CacheEntryCreatedListener<IgniteRegistrationInfo ,Boolean>,
                                                           CacheEntryRemovedListener<IgniteRegistrationInfo ,Boolean>,
                                                           CacheEntryExpiredListener<IgniteRegistrationInfo ,Boolean>,
                                                           Serializable {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionInfoCacheEntryListener.class);

    private final FluxSink<ListenerStatus> sink;
    private final Context vertxContext;

    public SubscriptionInfoCacheEntryListener(FluxSink<ListenerStatus> sink,
                                              Context vertxContext) {
        this.sink = sink;
        this.vertxContext = vertxContext;
    }

    @Override
    public void onCreated(Iterable<CacheEntryEvent<? extends IgniteRegistrationInfo, ? extends Boolean>> cacheEntryEvents) throws CacheEntryListenerException {
        log.trace("Subscription Status Listener called Created");
        vertxContext.runOnContext(event -> sink.next(ListenerStatus.ACTIVE));
    }

    @Override
    public void onExpired(Iterable<CacheEntryEvent<? extends IgniteRegistrationInfo, ? extends Boolean>> cacheEntryEvents) throws CacheEntryListenerException {
        log.trace("Subscription Status Listener called Expired");
        vertxContext.runOnContext(event -> sink.next(ListenerStatus.INACTIVE));
    }

    @Override
    public void onRemoved(Iterable<CacheEntryEvent<? extends IgniteRegistrationInfo, ? extends Boolean>> cacheEntryEvents) throws CacheEntryListenerException {
        log.trace("Subscription Status Listener called Removed");
        vertxContext.runOnContext(event -> sink.next(ListenerStatus.INACTIVE));
    }


}
