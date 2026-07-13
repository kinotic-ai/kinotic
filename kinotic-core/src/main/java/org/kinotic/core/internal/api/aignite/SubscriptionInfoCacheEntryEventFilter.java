

package org.kinotic.core.internal.api.aignite;

import java.io.Serializable;
import java.util.Set;

import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryListenerException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.spi.cluster.ignite.impl.IgniteRegistrationInfo;

/**
 * {@link CacheEntryEventFilter} that matches subscription cache entries for a single event bus address.
 * Created by 🤓 on 5/8/21.
 */
public class SubscriptionInfoCacheEntryEventFilter implements CacheEntryEventFilter<String, Set<IgniteRegistrationInfo>>, Serializable {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionInfoCacheEntryEventFilter.class);

    private final String cri;

    public SubscriptionInfoCacheEntryEventFilter(String cri) {
        this.cri = cri;
    }

    @Override
    public boolean evaluate(CacheEntryEvent<? extends String, ? extends Set<IgniteRegistrationInfo>> event) throws CacheEntryListenerException {
        boolean match = event.getKey().equals(cri);
        if(log.isTraceEnabled()) {
            log.trace("Subscription Status: {} Received for {} waiting for {}{}",
                      event.getEventType().name(),
                      event.getKey(),
                      cri,
                      match ? " they match." : " they don't match.");
        }
        return match;
    }
}
