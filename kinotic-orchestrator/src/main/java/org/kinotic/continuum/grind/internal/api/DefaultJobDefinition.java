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

package org.kinotic.continuum.grind.internal.api;

import org.kinotic.continuum.grind.api.JobDefinition;
import org.kinotic.continuum.grind.api.JobScope;
import org.kinotic.continuum.grind.api.Step;
import org.kinotic.continuum.grind.api.Task;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * NOTE: should not be instantiated directly
 *
 * Created by Navid Mitchell on 3/19/20
 */
public class DefaultJobDefinition implements JobDefinition {

    private final String description;
    private final JobScope jobScope;
    private final boolean parallel;

    private final LinkedList<Step> steps = new LinkedList<>();


    public DefaultJobDefinition(String description, JobScope jobScope, boolean parallel) {
        this.description = description != null ? description : UUID.randomUUID().toString();
        this.jobScope = jobScope;
        this.parallel = parallel;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean isParallel() {
        return parallel;
    }

    @Override
    public JobScope getScope() {
        return jobScope;
    }

    @Override
    public JobDefinition task(Task<?> task) {
        steps.add(new TaskStep(steps.size() + 1, task));
        return this;
    }

    @Override
    public JobDefinition taskStoreResult(Task<?> task) {
        steps.add(new TaskStep(steps.size() + 1, task, true));
        return this;
    }

    @Override
    public JobDefinition taskStoreResult(Task<?> task, String variableName) {
        steps.add(new TaskStep(steps.size() + 1, task, true, variableName));
        return this;
    }

    @Override
    public JobDefinition jobDefinition(JobDefinition jobDefinition) {
        steps.add(new JobDefinitionStep(steps.size() + 1, jobDefinition));
        return this;
    }

    public List<Step> getSteps(){
        return steps;
    }
}
