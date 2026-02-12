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

package org.kinotic.rpc.internal.api.event;

import org.kinotic.rpc.api.event.Metadata;

import java.util.Iterator;
import java.util.Map;

/**
 * Adapts a {@link Map} to a {@link Metadata}
 *
 * Created by navid on 11/21/19
 */
public class MapMetadataAdapter implements Metadata {

    private final Map<String, String> mapDelegate;

    public MapMetadataAdapter(Map<String, String> mapDelegate) {
        this.mapDelegate = mapDelegate;
    }

    public String get(String key) {
        return mapDelegate.get(key);
    }

    @Override
    public void put(String key, String value) {
        mapDelegate.put(key, value);
    }

    public void remove(String key) {
        mapDelegate.remove(key);
    }

    @Override
    public boolean contains(String key) {
        return mapDelegate.containsKey(key);
    }

    @Override
    public void clear() {
        mapDelegate.clear();
    }

    @Override
    public boolean isEmpty() {
        return mapDelegate.isEmpty();
    }

    @Override
    public int size() {
        return mapDelegate.size();
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return mapDelegate.entrySet().iterator();
    }

}
