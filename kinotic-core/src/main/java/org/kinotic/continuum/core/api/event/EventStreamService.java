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

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Provides functionality to send and receive events from the event streams.
 * Event streams are persistent events that are maintained throughout the cluster.
 *
 *
 * Created by navid on 10/22/19
 */
public interface EventStreamService {

    Mono<Void> send(Event<byte[]> event);

    /**
     * Sends a stream of events to the underlying stream storage
     * The returned Mono completes successfully if all the outbound records are delivered successfully.
     * The {@link Mono} terminates on the first send failure.
     * If publisher is a non-terminating {@link Flux}, records continue to be sent to the underlying stream storage unless a send fails or the returned Mono is cancelled.
     * @param publisher to receive events from
     * @return the {@link Mono} representing this request
     */
    Mono<Void> sendStream(Publisher<Event<byte[]>> publisher);

    Flux<Event<byte[]>> listen(CRI cri);
}

