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

package org.kinotic.continuum.grind.api;

import reactor.core.publisher.Flux;

/**
 *
 * Created by Navid Mitchell on 3/19/20
 */
public interface JobService {

    /**
     * Takes the given {@link JobDefinition} and assembles a {@link Flux} that when subscribed to will execute all of the {@link Task}'s within the {@link JobDefinition}
     * @param jobDefinition to assemble
     * @return the {@link Flux} that will execute the {@link JobDefinition}
     */
    Flux<Result<?>> assemble(JobDefinition jobDefinition);

    /**
     * Takes the given {@link JobDefinition} and assembles a {@link Flux} that when subscribed to will execute all of the {@link Task}'s within the {@link JobDefinition}
     * @param jobDefinition to assemble
     * @param options the {@link ResultOptions} to use when executing the {@link JobDefinition}
     *               this will determine the {@link ResultType}'s that you will receive from the emitted {@link Result}'s
     * @return the {@link Flux} that will execute the {@link JobDefinition}
     */
    Flux<Result<?>> assemble(JobDefinition jobDefinition, ResultOptions options);

}
