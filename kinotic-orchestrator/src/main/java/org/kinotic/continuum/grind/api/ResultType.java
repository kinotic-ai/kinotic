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
 * Created by Navid Mitchell on 11/11/20
 */
public enum ResultType {
    /**
     * The result value is the final VALUE of the task
     */
    VALUE,
    /**
     * The task resulted in no action being taken the value will be null
     */
    NOOP,
    /**
     * The result value is a Diagnostic message
     */
    DIAGNOSTIC,
    /**
     * The result value is a {@link Progress} object
     */
    PROGRESS,
    /**
     * Result contains new {@link Step}'s that have been returned by a {@link Task} execution
     * This is used to update the known {@link Step}'s when wanting to receive progress notifications
     * The result value will contain the new {@link Step}
     */
    DYNAMIC_STEPS,
    /**
     * The result value is a {@link Throwable} indicating that an error occurred at the given step
     */
    EXCEPTION
}
