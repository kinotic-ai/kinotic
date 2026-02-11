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

/**
 * Handles responses from remote service invocations and translates them into Java side objects returned from proxy invocations
 *
 * Created by navid on 2019-04-24.
 */
public interface RpcReturnValueHandler {

    /**
     * Get the return value that should be returned during the proxy invocation
     * This method will only be called once. And will be called before any other methods of the {@link RpcReturnValueHandler}.
     *
     * NOTE: {@link RpcRequest#send()} must be called by the {@link RpcReturnValueHandler} in all cases
     *
     * @param rpcRequest allows the actual request to be deferred and invoked by the return value at the appropriate time
     * @return the object that should be returned from the proxy invocation
     */
    Object getReturnValue(RpcRequest rpcRequest);

    /**
     * Return {@code true} if the return type can produce more than 1 value
     * can be produced and is therefore a good fit to adapt to {@link reactor.core.publisher.Flux}.
     * A {@code false} return value implies the return type can produce 1
     * value at most and is therefore a good fit to adapt to {@link reactor.core.publisher.Mono}.
     * This is used to determine the correct values to send to the remote end.
     */
    boolean isMultiValue();

    /**
     * Handle the incoming event and convert it to the desired result
     * @param incomingEvent to handle
     * @return true if this handler is done and does not expect any more events false if more events are expected by this handler
     */
    boolean processResponse(Event<byte[]> incomingEvent);

    /**
     * This is called when an error occurs sending the {@link RpcRequest}
     * In this case {@link RpcReturnValueHandler#processResponse} will never be called
     *
     * @param throwable the exception that occurred
     */
    void processError(Throwable throwable);

    /**
     * Can be called if processResponse will not be called again such as when the system shuts down.
     * @param message the reason that the response will never be processed
     */
    void cancel(String message);

}
