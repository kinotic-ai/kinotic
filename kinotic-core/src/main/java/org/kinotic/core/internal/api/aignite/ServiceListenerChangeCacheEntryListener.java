package org.kinotic.core.internal.api.aignite;

import io.vertx.core.Context;
import io.vertx.spi.cluster.ignite.impl.IgniteRegistrationInfo;
import org.kinotic.core.api.event.ListenerChange;
import org.kinotic.core.api.event.ListenerStatus;
import reactor.core.publisher.FluxSink;

import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.event.CacheEntryRemovedListener;
import java.io.Serializable;

/**
 * Emits a {@link ListenerChange} per subscription cache event onto a {@link FluxSink}, carrying the changed address so
 * one listener can feed registration deltas for every service address.
 */
public class ServiceListenerChangeCacheEntryListener implements CacheEntryCreatedListener<IgniteRegistrationInfo, Boolean>,
                                                                CacheEntryRemovedListener<IgniteRegistrationInfo, Boolean>,
                                                                CacheEntryExpiredListener<IgniteRegistrationInfo, Boolean>,
                                                                Serializable {

    private final FluxSink<ListenerChange> sink;
    private final Context vertxContext;

    public ServiceListenerChangeCacheEntryListener(FluxSink<ListenerChange> sink, Context vertxContext) {
        this.sink = sink;
        this.vertxContext = vertxContext;
    }

    @Override
    public void onCreated(Iterable<CacheEntryEvent<? extends IgniteRegistrationInfo, ? extends Boolean>> events) throws CacheEntryListenerException {
        emit(events, ListenerStatus.ACTIVE);
    }

    @Override
    public void onRemoved(Iterable<CacheEntryEvent<? extends IgniteRegistrationInfo, ? extends Boolean>> events) throws CacheEntryListenerException {
        emit(events, ListenerStatus.INACTIVE);
    }

    @Override
    public void onExpired(Iterable<CacheEntryEvent<? extends IgniteRegistrationInfo, ? extends Boolean>> events) throws CacheEntryListenerException {
        emit(events, ListenerStatus.INACTIVE);
    }

    private void emit(Iterable<CacheEntryEvent<? extends IgniteRegistrationInfo, ? extends Boolean>> events,
                      ListenerStatus status) {
        for (CacheEntryEvent<? extends IgniteRegistrationInfo, ? extends Boolean> event : events) {
            String address = event.getKey().address();
            vertxContext.runOnContext(ignored -> sink.next(new ListenerChange(address, status)));
        }
    }
}
