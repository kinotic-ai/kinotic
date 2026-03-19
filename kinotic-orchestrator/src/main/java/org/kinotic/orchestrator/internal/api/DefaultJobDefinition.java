

package org.kinotic.orchestrator.internal.api;

import org.kinotic.orchestrator.api.JobDefinition;
import org.kinotic.orchestrator.api.JobScope;
import org.kinotic.orchestrator.api.Step;
import org.kinotic.orchestrator.api.Task;

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
