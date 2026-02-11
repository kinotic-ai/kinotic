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

import org.kinotic.continuum.core.api.event.CRI;
import org.kinotic.continuum.core.api.event.Event;
import org.kinotic.continuum.core.api.event.EventConstants;
import org.kinotic.continuum.core.api.event.Metadata;
import io.vertx.core.eventbus.Message;

/**
 * {@link Event} implementation backed by a {@link Message}
 *
 * Created by navid on 10/30/19
 */
public class MessageEventAdapter<T> implements Event<T> {

    private final Message<T> vertxMessage;
    private final MultiMapMetadataAdapter metadata;
    private final CRI cri;

    public MessageEventAdapter(Message<T> vertxMessage) {
        this.vertxMessage = vertxMessage;
        this.metadata = new MultiMapMetadataAdapter(vertxMessage.headers());
        this.cri = CRI.create(vertxMessage.headers().get(EventConstants.CRI_HEADER));
        vertxMessage.headers().remove(EventConstants.CRI_HEADER);
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
        return vertxMessage.body();
    }

    public Message<T> getMessage(){
        return vertxMessage;
    }

}
