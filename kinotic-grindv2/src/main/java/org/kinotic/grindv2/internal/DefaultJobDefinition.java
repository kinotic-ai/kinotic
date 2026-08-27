package org.kinotic.grindv2.internal;

import lombok.Getter;
import org.apache.commons.lang3.Validate;
import org.kinotic.grindv2.api.JobDefinition;
import org.kinotic.grindv2.api.JobScope;
import org.kinotic.grindv2.api.StoreType;
import org.kinotic.grindv2.api.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The step tree behind a {@link JobDefinition}, built by the fluent methods and walked by the
 * {@link JobInterpreter}.
 */
public class DefaultJobDefinition implements JobDefinition {

    @Getter
    private final String description;

    @Getter
    private final JobScope scope;

    @Getter
    private final boolean parallel;

    @Getter
    private String name;

    @Getter
    private String version;

    private final List<StepNode> steps = new ArrayList<>();

    private final List<Object> inputs = new ArrayList<>();

    public DefaultJobDefinition(String description, JobScope scope, boolean parallel) {
        this.description = description;
        this.scope = scope;
        this.parallel = parallel;
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
    public JobDefinition input(Object... values) {
        for (Object value : values) {
            Validate.notNull(value, "input values cannot be null");
            inputs.add(value);
        }
        return this;
    }

    @Override
    public JobDefinition task(Task<?> task) {
        steps.add(new TaskNode(steps.size() + 1, task, null, StoreType.NONE, null));
        return this;
    }

    @Override
    public JobDefinition taskStoreResult(Task<?> task) {
        steps.add(new TaskNode(steps.size() + 1, task, null, StoreType.RESULT, null));
        return this;
    }

    @Override
    public JobDefinition taskStoreResult(Task<?> task, String resultName) {
        steps.add(new TaskNode(steps.size() + 1, task, null, StoreType.RESULT, resultName));
        return this;
    }

    @Override
    public JobDefinition taskStoreResult(Task<?> createTask, Task<?> reloadTask) {
        steps.add(new TaskNode(steps.size() + 1, createTask, reloadTask, StoreType.RESULT, null));
        return this;
    }

    @Override
    public JobDefinition taskStoreResult(Task<?> createTask, Task<?> reloadTask, String resultName) {
        steps.add(new TaskNode(steps.size() + 1, createTask, reloadTask, StoreType.RESULT, resultName));
        return this;
    }

    @Override
    public JobDefinition taskStoreState(Task<?> task) {
        steps.add(new TaskNode(steps.size() + 1, task, null, StoreType.STATE, null));
        return this;
    }

    @Override
    public JobDefinition taskStoreState(Task<?> task, String resultName) {
        steps.add(new TaskNode(steps.size() + 1, task, null, StoreType.STATE, resultName));
        return this;
    }

    @Override
    public JobDefinition jobDefinition(JobDefinition jobDefinition) {
        steps.add(new DefinitionNode(steps.size() + 1, (DefaultJobDefinition) jobDefinition));
        return this;
    }

    /**
     * The nodes of this definition in execution order.
     */
    public List<StepNode> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    /**
     * The values seeded into the job scope before the first step runs.
     */
    public List<Object> getInputs() {
        return Collections.unmodifiableList(inputs);
    }

}
