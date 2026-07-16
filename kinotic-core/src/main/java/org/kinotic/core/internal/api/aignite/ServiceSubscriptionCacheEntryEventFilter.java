package org.kinotic.core.internal.api.aignite;

import io.vertx.spi.cluster.ignite.impl.IgniteRegistrationInfo;

import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryListenerException;
import java.io.Serializable;

/**
 * {@link CacheEntryEventFilter} that accepts subscription cache events whose address is a service ({@code srv://})
 * address, so a single listener can observe registration changes for every service.
 */
public class ServiceSubscriptionCacheEntryEventFilter implements CacheEntryEventFilter<IgniteRegistrationInfo, Boolean>, Serializable {

    private final String serviceAddressPrefix;

    public ServiceSubscriptionCacheEntryEventFilter(String serviceAddressPrefix) {
        this.serviceAddressPrefix = serviceAddressPrefix;
    }

    @Override
    public boolean evaluate(CacheEntryEvent<? extends IgniteRegistrationInfo, ? extends Boolean> event) throws CacheEntryListenerException {
        return event.getKey().address().startsWith(serviceAddressPrefix);
    }
}
