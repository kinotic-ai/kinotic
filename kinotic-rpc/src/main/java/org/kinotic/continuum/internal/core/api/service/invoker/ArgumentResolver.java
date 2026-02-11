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

/**
 * Supports resolving arguments from an incoming {@link Event (byte[])}
 *
 * NOTE: All implementations should be thread safe.
 *
 * Created by Navid Mitchell on 2019-03-29.
 */
public interface ArgumentResolver {

    /**
     * Checks if the given {@link Event} is supported by this resolver
     *
     * @param incomingEvent to check if supported
     * @return true if this resolver can resolve arguments false if not
     */
    boolean supports(Event<byte[]> incomingEvent);

    /**
     * Resolves the arguments from the given {@link Event}
     *
     * @param incomingEvent to resolve arguments from
     * @param handlerMethod the {@link HandlerMethod} that will be invoked after the arguments are resolved
     * @return an Object array with all arguments in the order they should be provided to the method to be invoked
     */
    Object[] resolveArguments(Event<byte[]> incomingEvent, HandlerMethod handlerMethod);

}
