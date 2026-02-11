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

package org.kinotic.continuum.internal.utils;

import org.apache.commons.lang3.Validate;
import org.kinotic.continuum.core.api.event.Event;
import org.kinotic.continuum.core.api.event.EventConstants;
import org.kinotic.continuum.core.api.event.Metadata;
import org.kinotic.continuum.internal.core.api.service.invoker.ServiceInvocationSupervisor;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.function.Supplier;

/**
 *
 * Created by navid on 2019-07-24.
 */
public class EventUtil {

    /**
     * Creates a {@link Event} that can be sent based on the incomingEvent headers and the data to use as the body
     * @param incomingMetadata the original {@link Metadata} sent to the {@link ServiceInvocationSupervisor}
     * @param headers key value pairs that will be added to the outgoing headers
     * @param bodySupplier that will provide the bytes needed for the message body.
     *                     A supplier is used so all validations can occur before doing the work of creating the body bytes.
     * @return the {@link Event} to send
     */
    public static Event<byte[]> createReplyEvent(Metadata incomingMetadata, Map<String, String> headers, Supplier<byte[]> bodySupplier){
        Validate.notNull(incomingMetadata, "incomingEvent cannot be null");

        String replyCRI = incomingMetadata.get(EventConstants.REPLY_TO_HEADER);
        Assert.hasText(replyCRI, "No reply-to header found cannot create outgoing message");

        Metadata newMetadata;
        if(headers != null){
            newMetadata = Metadata.create(headers);
        }else{
            newMetadata = Metadata.create();
        }

        // we must persist any headers that begin with __
        for(Map.Entry<String, String> entry: incomingMetadata){
            if(entry.getKey().startsWith("__")) {
                newMetadata.put(entry.getKey(), entry.getValue());
            }
        }

        return Event.create(replyCRI, newMetadata, bodySupplier != null ?  bodySupplier.get() : null);
    }

    public static String toString(Event<byte[]> event, boolean includeData) {
        StringBuilder sb = new StringBuilder("Event<byte>{\n");
        sb.append("\tcri=");
        sb.append(event.cri());
        sb.append("\n");
        sb.append("\tmetadata={\n");
        if (event.metadata() != null) {
            for (Map.Entry<String, String> entry : event.metadata()) {
                sb.append("\t\t");
                sb.append(entry.getKey());
                sb.append(": ");
                sb.append(entry.getValue());
                sb.append("\n");
            }
        }
        sb.append("\t}\n");

        if(includeData && event.data() != null && event.data().length > 0){
            sb.append("\tdata=\n\t\t");
            sb.append(new String(event.data()));
        }

        sb.append("\n}");

        return sb.toString();
    }






}
