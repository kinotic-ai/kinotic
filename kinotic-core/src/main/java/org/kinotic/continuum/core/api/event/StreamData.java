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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.kinotic.continuum.api.Identifiable;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 *
 * Created by Navid Mitchell on 6/4/20
 */
public class StreamData<I,T> implements Identifiable<I> {

    @JsonProperty
    private final StreamOperation streamOperation;
    @JsonProperty
    private final I identity;
    @JsonProperty
    private final T value;

    public StreamData(StreamOperation streamOperation, I identity, T value) {
        Validate.notNull(streamOperation, "streamOperation must not be null");
        Validate.notNull(identity, "identity must not be null");
        this.streamOperation = streamOperation;
        this.identity = identity;
        this.value = value;
    }

    public StreamOperation streamOperation() {
        return streamOperation;
    }

    public I getId() {
        return identity;
    }

    public T value() {
        return value;
    }

    @JsonIgnore
    public boolean isSet(){
        return value() != null;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("streamOperation", streamOperation)
                .append("identity", identity)
                .append("value", value)
                .toString();
    }

}
