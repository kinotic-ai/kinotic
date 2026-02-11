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

import java.util.Map;

/**
 * {@link Metadata} is {@link String} key value pairs containing useful information about an {@link Event}
 *
 * Created by navid on 10/29/19
 */
public interface Metadata extends Iterable<Map.Entry<String, String>> {

    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this {@link Metadata} contains no mapping for the key.
     *
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or
     *         {@code null} if this map contains no mapping for the key
     */
    String get(String key);

    /**
     * Associates the specified value with the specified key in this {@link Metadata}
     * If the {@link Metadata} previously contained a mapping for
     * the key, the old value is replaced by the specified value.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     */
    void put(String key, String value);

    /**
     * Removes the value for a key from this {@link Metadata} if it is present.
     * @param key key whose mapping is to be removed from the {@link Metadata}
     */
    void remove(String key);

    /**
     * Returns {@code true} if this {@link Metadata} contains a mapping for the specified
     * key.
     *
     * @param key key whose presence in this {@link Metadata} is to be tested
     * @return {@code true} if this map contains a mapping for the specified key
     */
    boolean contains(String key);

    /**
     * Removes all
     */
    void clear();

    /**
     * @return true if empty
     */
    boolean isEmpty();

    /**
     * @return  number of key value pairs contained in this {@link Metadata}
     */
    int size();

    static Metadata create(){
        return new DefaultMetadata();
    }

    static Metadata create(Map<String, String> map){
        return new DefaultMetadata(map);
    }

}
