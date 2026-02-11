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

import org.kinotic.continuum.core.api.event.StreamData;
import org.kinotic.continuum.internal.utils.IgniteUtil;
import groovy.lang.Binding;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryListenerException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Navid Mitchell on 8/2/17.
 */
public class ScriptCacheEntryEventFilter<K, V> implements CacheEntryEventFilter<K, V>, Serializable{
    private static final Logger log = LoggerFactory.getLogger(ScriptCacheEntryEventFilter.class);
    private final String scriptSource;
    private final ScriptFilter filterScript;

    public ScriptCacheEntryEventFilter(String scriptSource, ScriptFilter filterScript,Object[] args) {
        this.scriptSource = scriptSource;
        this.filterScript = filterScript;
        if(args.length > 0){
            Map<String,Object> map = new HashMap<>();
            map.put("args",args);
            Binding binding = new Binding(map);
            this.filterScript.setBinding(binding);
        }
    }

    @Override
    public boolean evaluate(CacheEntryEvent<? extends K, ? extends V> event) throws CacheEntryListenerException {
        StreamData<K,V> streamValue = null;
        try {
            streamValue = IgniteUtil.cacheEntryEventToStreamData(event);
            filterScript.setDelegate(streamValue);
            Object ret = filterScript.run();

            if(ret instanceof Boolean){
                return (boolean) ret;
            }else{
                throw new CacheEntryListenerException("Entry Filter script did not return a boolean");
            }
        } catch (Exception e) {
            log.debug("Error occurred while running filter script "+e.getMessage());
            log.debug("Script\n"+scriptSource);
            log.debug("StreamValue: "+(streamValue != null? streamValue.toString(): "null"));
            log.debug("Binding -> "+ToStringBuilder.reflectionToString(filterScript.getBinding().getVariables()));
            throw e;
        }
    }
}
