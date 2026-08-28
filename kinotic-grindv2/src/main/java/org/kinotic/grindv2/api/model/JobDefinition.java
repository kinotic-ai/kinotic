package org.kinotic.grindv2.api.model;

import org.kinotic.grindv2.internal.api.model.DefaultJobDefinition;
import org.kinotic.grindv2.internal.api.services.TaskClassCompiler;

/**
 * A {@link JobDefinition} is a unit of work comprised of {@link Task}s and nested
 * {@link JobDefinition}s, executed by a {@link JobService}. Tasks share a {@link JobContext}
 * scope, so values stored by earlier tasks can be injected into later ones.
 *
 * A definition is one run's worth of bound tasks: its {@link Task} instances receive
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
     * True if this definition's tasks execute concurrently instead of sequentially.
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
     * Seeds the given values into the job scope before the first task runs, each stored as a
     * bean so tasks can inject it by type.
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
     * Adds a {@link Task} whose result is kept as the given {@link Store} declares: its
     * {@link StoreType} governs resume behavior, its name places the value in the job scope
     * for later tasks, and its wire flag publishes the value to watchers of the run.
     * @param task to add
     * @param store how the result is kept
     * @return this for fluent use
     */
    JobDefinition task(Task<?> task, Store store);

    /**
     * Adds a nested {@link JobDefinition} executed as one task of this definition.
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
     * @param parallel true to execute the definition's tasks concurrently
     * @return the new definition
     */
    static JobDefinition create(String description, JobScope scope, boolean parallel) {
        return new DefaultJobDefinition(description, scope, parallel);
    }

    /**
     * Compiles a tasks class into a {@link JobDefinition}: each {@link Task} method becomes
     * one task, executed in {@link Task#order()}. The class is instantiated once per run with
     * constructor arguments resolved against the application context - it is never a Spring
     * bean itself. Method parameters are injected from the job scope by type, and return
     * values are stored back into the scope under the method's {@link Task#store()} mode.
     * @param taskClass the class to compile
     * @return the definition
     * @throws IllegalArgumentException if the class declares no tasks, duplicate orders, or a
     *         task consuming a type that only a later task produces
     */
    static JobDefinition fromTasks(Class<?> taskClass) {
        return TaskClassCompiler.compile(taskClass);
    }

}
