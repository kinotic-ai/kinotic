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

/**
 *
 * Created by Navid Mitchell on 3/19/20
 */
public interface Result<T> {


    /**
     * The {@link StepInfo} which represents the {@link Step}'s that are responsible for creating this {@link Result}
     * @return the {@link StepInfo} for this {@link Result}
     */
    StepInfo getStepInfo();

    /**
     * What type of result this is.
     * The results that are produced by a {@link JobDefinition} depend on what options you pass into the {@link JobService} during assembly
     *
     * The result type will affect the meaning of the value
     * For {@link ResultType#VALUE} the value will be the "final" value produced by a {@link Task}
     * For {@link ResultType#NOOP} the value will be null
     * For {@link ResultType#DIAGNOSTIC} the value will be a simple message describing something that happened
     * For {@link ResultType#PROGRESS} the value will be a {@link Progress} object
     * For {@link ResultType#EXCEPTION} the value will be a {@link Throwable} object
     *
     * @return the {@link ResultType} for this result
     */
    ResultType getResultType();

    /**
     * @return the result of a successful execution of the associated {@link Step}
     */
    T getValue();

}
