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

package org.kinotic.continuum.gateway.internal.hft;

import org.kinotic.continuum.core.api.event.Event;
import reactor.core.publisher.Mono;

/**
 *
 * Created by Navid Mitchell on 11/4/20
 */
public interface HFTQueueManager {

    /**
     * Writes an {@link Event} to a HFT queue
     *
     * @param event to write to the queue
     *
     * @return a {@link Mono} that will succeed on successful writing and fail if there is an error
     */
    Mono<Void> write(Event<byte[]> event);

}
