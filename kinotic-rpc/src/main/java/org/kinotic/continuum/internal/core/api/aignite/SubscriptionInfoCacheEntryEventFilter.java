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

package org.kinotic.continuum.internal.core.api.aignite;

import java.io.Serializable;

import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryListenerException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.spi.cluster.ignite.impl.IgniteNodeInfo;
import io.vertx.spi.cluster.ignite.impl.IgniteRegistrationInfo;

/**
 * {@link CacheEntryEventFilter} for {@link IgniteNodeInfo}
 * Created by 🤓 on 5/8/21.
 */
public class SubscriptionInfoCacheEntryEventFilter implements CacheEntryEventFilter<IgniteRegistrationInfo, Boolean>, Serializable {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionInfoCacheEntryEventFilter.class);

    private final String cri;

    public SubscriptionInfoCacheEntryEventFilter(String cri) {
        this.cri = cri;
    }

    @Override
    public boolean evaluate(CacheEntryEvent<? extends IgniteRegistrationInfo, ? extends Boolean> event) throws CacheEntryListenerException {
        boolean match = event.getKey().address().equals(cri);
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
