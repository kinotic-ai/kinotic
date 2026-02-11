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

import org.reactivestreams.Publisher;
import org.springframework.context.support.GenericApplicationContext;

/**
 * A step in a {@link JobDefinition}
 *
 * Created by Navid Mitchell on 3/25/20
 */
public interface Step {

    /**
     * This is the sequence for this step in the {@link JobDefinition} the first {@link Task} would create a {@link Step} with a sequence of one and so on
     * @return the sequence for this step
     */
    int getSequence();

    /**
     * The description comes from the {@link Task} or {@link JobDefinition}
     * that this step was created for
     * @return the description of this {@link Step}
     */
    String getDescription();

    /**
     * Prepares the {@link Step} for execution.
     *
     * @param applicationContext the execution context that will be used for this {@link Step}
     * @param options the {@link ResultOptions} to use when executing the {@link JobDefinition}
     *               this will determine the {@link ResultType}'s that you will receive from the emitted {@link Result}'s
     * @return a {@link Publisher} that when subscribed to will create the result for this {@link Step}
     */
    Publisher<Result<?>> assemble(GenericApplicationContext applicationContext, ResultOptions options);

}
