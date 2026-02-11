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
 * The "Scope" that a {@link JobDefinition} should be executed in.
 * This affects where the {@link Task} results from the job will be stored if required
 *
 *
 * Created by Navid Mitchell on 8/5/20
 */
public enum JobScope {

    /**
     * The {@link JobDefinition} will use the scope of the parent {@link JobDefinition}
     */
    PARENT,

    /**
     * The {@link JobDefinition} will create a new scope that is the child of the parent scope
     */
    CHILD,

    /**
     * The {@link JobDefinition} will create a new scope that is independent of all other scopes
     */
    ISOLATED

}
