

package org.kinotic.system.internal.api.model.grind;

import org.kinotic.domain.api.model.grind.StoreType;
import org.kinotic.system.api.model.grind.JobDefinition;
import org.kinotic.system.api.model.grind.JobScope;
import org.kinotic.system.api.model.grind.Task;

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
    private String name;
    private String version;

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
    public String getName() {
        return name;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public JobDefinition name(String name) {
        this.name = name;
        return this;
    }

    @Override
    public JobDefinition version(String version) {
        this.version = version;
        return this;
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
        steps.add(new TaskStep(steps.size() + 1, task, null, StoreType.RESULT, null));
        return this;
    }

    @Override
    public JobDefinition taskStoreResult(Task<?> task, String variableName) {
        steps.add(new TaskStep(steps.size() + 1, task, null, StoreType.RESULT, variableName));
        return this;
    }

    @Override
    public JobDefinition taskStoreResult(Task<?> createTask, Task<?> reloadTask) {
        steps.add(new TaskStep(steps.size() + 1, createTask, reloadTask, StoreType.RESULT, null));
        return this;
    }

    @Override
    public JobDefinition taskStoreResult(Task<?> createTask, Task<?> reloadTask, String variableName) {
        steps.add(new TaskStep(steps.size() + 1, createTask, reloadTask, StoreType.RESULT, variableName));
        return this;
    }

    @Override
    public JobDefinition taskStoreState(Task<?> task) {
        steps.add(new TaskStep(steps.size() + 1, task, null, StoreType.STATE, null));
        return this;
    }

    @Override
    public JobDefinition taskStoreState(Task<?> task, String variableName) {
        steps.add(new TaskStep(steps.size() + 1, task, null, StoreType.STATE, variableName));
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
