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

package org.kinotic.continuum.internal.core.api.service.rpc;

import org.kinotic.continuum.core.api.event.Event;
import org.springframework.core.MethodParameter;

/**
 * Converts responses to remote service invocations into a return value for a given proxy calls
 *
 *
 * Created by navid on 2019-04-23.
 */
public interface RpcResponseConverter {

    /**
     * Checks if this converter can convert the incoming response for the given {@link Class}
     * @param responseEvent the response received for the remote service invocation
     * @param methodParameter that is for the return type of the invoked method
     * @return true if this converter can convert the event false if not
     */
    boolean supports(Event<byte[]> responseEvent, MethodParameter methodParameter);

    /**
     * Converts the response {@link Event} into a Java Object to return to the caller
     * @param responseEvent the response received for the remote service invocation
     * @param methodParameter that is for the return type of the invoked method
     * @return the converted value
     */
    Object convert(Event<byte[]> responseEvent, MethodParameter methodParameter);


}
