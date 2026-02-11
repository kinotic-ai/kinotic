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

import org.kinotic.continuum.grind.internal.api.DefaultJobDefinition;

import java.util.List;

/**
 * A {@link JobDefinition} provides a unit of work comprised of {@link Task}'s and other {@link JobDefinition}
 * For every {@link JobDefinition} a Spring {@link org.springframework.context.ApplicationContext} is provided,
 * to allow {@link Task}'s to automatically store and access data produced by {@link Task}'s
 *
 * Created by Navid Mitchell on 3/19/20
 */
public interface JobDefinition extends HasSteps{

    /**
     * @return the description of this {@link JobDefinition}
     */
    String getDescription();

    /**
     * If this {@link JobDefinition} supports running it's  {@link Task}'s in parallel
     *
     * @return true if {@link Tasks}'s can be run in parallel false if not
     */
    boolean isParallel();

    /**
     * The {@link JobScope} that will be used during the execution of this  {@link JobDefinition}
     * @return the {@link JobScope} the default is {@link JobScope#CHILD}
     */
    JobScope getScope();

    /**
     * Adds a {@link Task} to the list of {@link Task}'s that will be executed by this {@link JobDefinition}
     * @param task to add
     * @return this for fluent use
     */
    JobDefinition task(Task<?> task);

    /**
     * Adds a {@link Task} to the list of {@link Task}'s that will be executed by this {@link JobDefinition}
     * and stores the result of the {@link Task} execution within the context for this {@link JobDefinition}
     * @param task to add
     * @return this for fluent use
     */
    JobDefinition taskStoreResult(Task<?> task);

    /**
     * Adds a {@link Task} to the list of {@link Task}'s that will be executed by this {@link JobDefinition}
     * and stores the result of the {@link Task} execution within the context for this {@link JobDefinition}
     * @param task to add
     * @param variableName the name to use when storing the {@link Task} result in the context for this {@link JobDefinition}
     * @return this for fluent use
     */
    JobDefinition taskStoreResult(Task<?> task, String variableName);

    /**
     * Adds a inner {@link JobDefinition} to this {@link JobDefinition}
     * @param jobDefinition to add into this {@link JobDefinition}
     * @return this for fluent use
     */
    JobDefinition jobDefinition(JobDefinition jobDefinition);

    /**
     * @return the {@link Step}'s defined for this {@link JobDefinition}
     */
    List<Step> getSteps();

    /**
     * Create a new {@link JobDefinition} with a {@link JobScope#CHILD} and {@link JobDefinition#isParallel()} is false
     * @return the new {@link JobDefinition}
     */
    static JobDefinition create(){
        return new DefaultJobDefinition(null, JobScope.CHILD, false);
    }


    static JobDefinition create(String description){
        return new DefaultJobDefinition(description, JobScope.CHILD, false);
    }

    static JobDefinition create(String description, JobScope jobScope){
        return new DefaultJobDefinition(description, jobScope, false);
    }

    static JobDefinition create(String description, JobScope jobScope, boolean parallel){
        return new DefaultJobDefinition(description, jobScope, parallel);
    }

    static JobDefinition create(String description, boolean parallel){
        return new DefaultJobDefinition(description, JobScope.CHILD, parallel);
    }


}
