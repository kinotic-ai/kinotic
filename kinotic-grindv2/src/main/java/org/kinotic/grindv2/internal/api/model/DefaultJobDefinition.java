package org.kinotic.grindv2.internal.api.model;

import org.kinotic.grindv2.internal.model.DefinitionNode;
import org.kinotic.grindv2.internal.model.JobNode;
import org.kinotic.grindv2.internal.model.TaskNode;
import lombok.Getter;
import org.apache.commons.lang3.Validate;
import org.kinotic.grindv2.api.model.JobDefinition;
import org.kinotic.grindv2.api.model.JobScope;
import org.kinotic.grindv2.api.model.Store;
import org.kinotic.grindv2.api.model.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The task tree behind a {@link JobDefinition}, built by the fluent methods and walked by the
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

    private final List<JobNode> tasks = new ArrayList<>();

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
        tasks.add(new TaskNode(tasks.size() + 1, task, Store.none()));
        return this;
    }

    @Override
    public JobDefinition task(Task<?> task, Store store) {
        Validate.notNull(store, "store cannot be null");
        tasks.add(new TaskNode(tasks.size() + 1, task, store));
        return this;
    }

    @Override
    public JobDefinition jobDefinition(JobDefinition jobDefinition) {
        tasks.add(new DefinitionNode(tasks.size() + 1, (DefaultJobDefinition) jobDefinition));
        return this;
    }

    /**
     * The nodes of this definition in execution order.
     */
    public List<JobNode> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    /**
     * The values seeded into the job scope before the first task runs.
     */
    public List<Object> getInputs() {
        return Collections.unmodifiableList(inputs);
    }

}
