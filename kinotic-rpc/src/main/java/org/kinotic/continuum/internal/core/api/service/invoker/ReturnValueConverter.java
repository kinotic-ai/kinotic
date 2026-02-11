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

package org.kinotic.continuum.internal.core.api.service.invoker;

import org.kinotic.continuum.core.api.event.Event;
import org.kinotic.continuum.core.api.event.Metadata;
import org.kinotic.continuum.core.api.event.EventBusService;

/**
 * Converts the return value to a {@link Event} that can bes sent on the {@link EventBusService}
 * NOTE: All implementations should be threadsafe.
 *
 *
 * Created by Navid Mitchell on 2019-03-29.
 */
public interface ReturnValueConverter {

    /**
     * Converts the return value to an {@link Event} to send
     * @param incomingMetadata the original {@link Metadata} sent to the {@link ServiceInvocationSupervisor}
     * @param returnType of the {@link java.lang.reflect.Method} that was invoked to get this return value
     * @param returnValue that was returned by the invoked method
     * @return the {@link Event} containing the converted data
     */
    Event<byte[]> convert(Metadata incomingMetadata, Class<?> returnType, Object returnValue);

    /**
     * Checks it a given {@link ReturnValueConverter} supports the incoming {@link Event} by checking the data provided
     * @param incomingMetadata the original {@link Metadata} sent to the {@link ServiceInvocationSupervisor}
     * @param returnType of the {@link java.lang.reflect.Method} that will be invoked
     * @return true if this converter can handle the data
     */
    boolean supports(Metadata incomingMetadata, Class<?> returnType);

}
