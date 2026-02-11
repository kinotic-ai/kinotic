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

/**
 * Event that contains both {@link Metadata} and an object instance
 *
 *
 * Created by navid on 10/29/19
 */
public interface Event<T> {

    /**
     * The {@link CRI} that specifies where the event should be routed
     *
     * @return the {@link CRI} for this event
     */
    CRI cri();

    /**
     * Metadata can provide additional information about this event to be used by the system.
     * @return the {@link Metadata} for this event
     */
    Metadata metadata();

    /**
     * @return the data object for this event or null if there is no data
     */
    T data();

    /**
     * Static method to create a default {@link Event} object
     * @param rawCRI raw uri that the event will be sent to
     * @param data the object instance that will be the data
     * @param <Z> the type of the data
     * @return the newly created {@link Event} object
     */
    static <Z> Event<Z> create(String rawCRI, Z data){
        return new DefaultEvent<>(CRI.create(rawCRI), data);
    }

    /**
     * Static method to create a default {@link Event} object
     * @param cri that the event will be sent to
     * @param data the object instance that will be the data
     * @param <Z> the type of the data
     * @return the newly created {@link Event} object
     */
    static <Z> Event<Z> create(CRI cri, Z data){
        return new DefaultEvent<>(cri, data);
    }

    /**
     * Static method to create a default {@link Event} object
     * @param rawCRI raw uri that the event will be sent to
     * @param metadata to be provided for the event
     * @param data the object instance that will be the data
     * @param <Z> the type of the data
     * @return the newly created {@link Event} object
     */
    static <Z> Event<Z> create(String rawCRI, Metadata metadata, Z data){
        return new DefaultEvent<>(CRI.create(rawCRI), metadata, data);
    }

    /**
     * Static method to create a default {@link Event} object
     * @param cri that the event will be sent to
     * @param metadata to be provided for the event
     * @param data the object instance that will be the data
     * @param <Z> the type of the data
     * @return the newly created {@link Event} object
     */
    static <Z> Event<Z> create(CRI cri, Metadata metadata, Z data){
        return new DefaultEvent<>(cri, metadata, data);
    }


}
