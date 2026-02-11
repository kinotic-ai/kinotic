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

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 *
 * Created by navid on 11/6/19
 */
public class DefaultEvent<T> implements Event<T>{

    private final CRI cri;
    private final Metadata metadata;
    private final T data;

    public DefaultEvent(CRI cri, Metadata metadata, T data) {
        Validate.notNull(cri, "CRI must not be null");
        Validate.notNull(cri, "metadata must not be null");
        this.cri = cri;
        this.metadata = metadata;
        this.data = data;
    }

    public DefaultEvent(CRI cri, T data) {
        this(cri, new DefaultMetadata(), data);
    }

    @Override
    public CRI cri() {
        return cri;
    }

    @Override
    public Metadata metadata() {
        return metadata;
    }

    @Override
    public T data() {
        return data;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("cri", cri)
                .append("metadata", metadata)
                .append("data", data)
                .toString();
    }
}
