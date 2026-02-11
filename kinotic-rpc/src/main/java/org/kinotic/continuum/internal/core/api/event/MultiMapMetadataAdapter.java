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

package org.kinotic.continuum.internal.core.api.event;

import org.kinotic.continuum.core.api.event.Metadata;
import io.vertx.core.MultiMap;

import java.util.Iterator;
import java.util.Map;

/**
 * {@link Metadata} implementation backed by a {@link MultiMap}
 *
 * Created by navid on 10/30/19
 */
public class MultiMapMetadataAdapter implements Metadata {

    private final MultiMap multiMap;

    public MultiMapMetadataAdapter(MultiMap multiMap) {
        this.multiMap = multiMap;
    }

    @Override
    public String get(String key) {
        return multiMap.get(key);
    }

    @Override
    public void put(String key, String value) {
        multiMap.set(key, value);
    }

    @Override
    public void remove(String key) {
        multiMap.remove(key);
    }

    @Override
    public boolean contains(String key) {
        return multiMap.contains(key);
    }

    @Override
    public void clear() {
        multiMap.clear();
    }

    @Override
    public boolean isEmpty() {
        return multiMap.isEmpty();
    }

    @Override
    public int size() {
        return multiMap.size();
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return multiMap.iterator();
    }

    public MultiMap getMultiMap(){
        return multiMap;
    }

}
