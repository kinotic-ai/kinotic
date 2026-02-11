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

package org.kinotic.continuum.core.api.event;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * Created by navid on 11/6/19
 */
class DefaultMetadata implements Metadata{

    private final Map<String,String> delegate;

    public DefaultMetadata() {
        delegate = new LinkedHashMap<>();
    }

    public DefaultMetadata(Map<String, String> delegate) {
        this.delegate = new LinkedHashMap<>(delegate);
    }

    @Override
    public boolean contains(String key) {
        return delegate.containsKey(key);
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return delegate.entrySet().iterator();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    public String get(String key) {
        return delegate.get(key);
    }

    @Override
    public void put(String key, String value) {
        delegate.put(key, value);
    }

    public void remove(String key) {
        delegate.remove(key);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

}
