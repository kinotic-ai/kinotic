package org.kinotic.grindv2.api.model;

import org.kinotic.grindv2.internal.api.model.DefaultJobDefinition;
import org.kinotic.grindv2.internal.api.services.StepsClassCompiler;

/**
 * A {@link JobDefinition} is a unit of work comprised of {@link Task}s and nested
 * {@link JobDefinition}s, executed by a {@link JobService}. Steps share a {@link JobContext}
 * scope, so values stored by earlier steps can be injected into later ones.
 *
 * A definition is one run's worth of bound steps: its {@link Task} instances receive
 * injection per execution, so build a fresh definition for each run - executing the same
 * instance concurrently is not supported.
 */
public interface JobDefinition {

    /**
     * @return the description of this {@link JobDefinition}
     */
    String getDescription();

    /**
     * The stable name identifying this job across runs, recorded with every execution.
     * Required by {@link JobService#run(JobDefinition, JobOwner)}, optional for definitions
     * that are only nested within others.
     * @return the name or null if none was set
     */
    String getName();

    /**
     * The version of this job's definition, recorded with every execution so a persisted run
     * can be matched to the code that produced it.
     * @return the version or null if none was set
     */
    String getVersion();

    /**
     * The {@link JobScope} this definition executes in when nested. The default is
     * {@link JobScope#CHILD}.
     * @return the scope
     */
    JobScope getScope();

    /**
     * True if this definition's steps execute concurrently instead of sequentially.
     * @return true when parallel
     */
    boolean isParallel();

    /**
     * Sets the stable name identifying this job across runs.
     * @param name to use
     * @return this for fluent use
     */
    JobDefinition name(String name);

    /**
     * Sets the version of this job's definition.
     * @param version to use
     * @return this for fluent use
     */
    JobDefinition version(String version);

    /**
     * Seeds the given values into the job scope before the first step runs, each stored as a
     * bean so steps can inject it by type.
     * @param values to seed
     * @return this for fluent use
     */
    JobDefinition input(Object... values);

    /**
     * Adds a {@link Task} whose result is not kept.
     * @param task to add
     * @return this for fluent use
     */
    JobDefinition task(Task<?> task);

    /**
     * Adds a {@link Task} and stores its result in the job scope for later steps. The stored
     * value is transient wiring: it is not persisted with the run's records, and on resume
     * the task re-executes to regenerate it, so it must be safe to re-run.
     * @param task to add
     * @return this for fluent use
     */
    JobDefinition taskStoreResult(Task<?> task);

    /**
     * Adds a {@link Task} and stores its result in the job scope under the given name, as
     * {@link #taskStoreResult(Task)}.
     * @param task to add
     * @param resultName the name to store the result under
     * @return this for fluent use
     */
    JobDefinition taskStoreResult(Task<?> task, String resultName);

    /**
     * Adds a {@link Task} that creates external state, paired with the {@link Task} that
     * reloads that state from its source of truth. On the first run {@code createTask}
     * executes; when a resumed run finds this step already completed, {@code reloadTask}
     * executes instead, so the creation is never repeated.
     * @param createTask executed to create the state and store the result
     * @param reloadTask executed on resume to reload the state and store the result
     * @return this for fluent use
     */
    JobDefinition taskStoreResult(Task<?> createTask, Task<?> reloadTask);

    /**
     * Adds a create/reload {@link Task} pair storing under the given name, as
     * {@link #taskStoreResult(Task, Task)}.
     * @param createTask executed to create the state and store the result
     * @param reloadTask executed on resume to reload the state and store the result
     * @param resultName the name to store the result under
     * @return this for fluent use
     */
    JobDefinition taskStoreResult(Task<?> createTask, Task<?> reloadTask, String resultName);

    /**
     * Adds a {@link Task} and stores its result as durable state: the value is serialized
     * into the run's {@link StepRecord}, and on resume it is replayed from the record instead
     * of executing the task again. See {@link StoreType#STATE} for what values qualify.
     * @param task to add
     * @return this for fluent use
     */
    JobDefinition taskStoreState(Task<?> task);

    /**
     * Adds a {@link Task} storing durable state under the given name, as
     * {@link #taskStoreState(Task)}.
     * @param task to add
     * @param resultName the name to store the result under
     * @return this for fluent use
     */
    JobDefinition taskStoreState(Task<?> task, String resultName);

    /**
     * Adds a nested {@link JobDefinition} executed as one step of this definition.
     * @param jobDefinition to nest
     * @return this for fluent use
     */
    JobDefinition jobDefinition(JobDefinition jobDefinition);

    /**
     * Creates a new sequential {@link JobDefinition} with a {@link JobScope#CHILD} scope.
     * @return the new definition
     */
    static JobDefinition create() {
        return new DefaultJobDefinition(null, JobScope.CHILD, false);
    }

    /**
     * Creates a new sequential {@link JobDefinition} with a {@link JobScope#CHILD} scope.
     * @param description of the definition
     * @return the new definition
     */
    static JobDefinition create(String description) {
        return new DefaultJobDefinition(description, JobScope.CHILD, false);
    }

    /**
     * Creates a new sequential {@link JobDefinition} with the given scope.
     * @param description of the definition
     * @param scope the definition executes in when nested
     * @return the new definition
     */
    static JobDefinition create(String description, JobScope scope) {
        return new DefaultJobDefinition(description, scope, false);
    }

    /**
     * Creates a new {@link JobDefinition}.
     * @param description of the definition
     * @param scope the definition executes in when nested
     * @param parallel true to execute the definition's steps concurrently
     * @return the new definition
     */
    static JobDefinition create(String description, JobScope scope, boolean parallel) {
        return new DefaultJobDefinition(description, scope, parallel);
    }

    /**
     * Compiles a steps class into a {@link JobDefinition}: each {@link Step} method becomes
     * one step, executed in {@link Step#order()}. The class is instantiated once per run with
     * constructor arguments resolved against the application context - it is never a Spring
     * bean itself. Method parameters are injected from the job scope by type, and return
     * values are stored back into the scope under the method's {@link Step#store()} mode.
     * @param stepsClass the class to compile
     * @return the definition
     * @throws IllegalArgumentException if the class declares no steps, duplicate orders, or a
     *         step consuming a type that only a later step produces
     */
    static JobDefinition fromSteps(Class<?> stepsClass) {
        return StepsClassCompiler.compile(stepsClass);
    }

}
