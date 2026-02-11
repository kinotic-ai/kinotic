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

/**
 * {@link RpcRequest} is provided so that {@link RpcReturnValueHandler}'s will be able to provided a deferred request pattern.
 * This is useful for objects that expect that the actual work will not be done until some method is performed on the return value.
 * Such as subscribing to a {@link reactor.core.publisher.Mono} or {@link reactor.core.publisher.Flux}
 *
 *
 * Created by navid on 10/30/19
 */
public interface RpcRequest {

    /**
     * Sends an event that will trigger the invocation on the remote end
     */
    void send();

    /**
     * Sends a control event to the remote end to cancel a long running invocation
     */
    void cancelRequest();

}
