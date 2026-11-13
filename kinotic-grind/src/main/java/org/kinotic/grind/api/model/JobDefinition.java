package org.kinotic.grind.api.model;

import org.kinotic.grind.api.services.JobService;
import org.kinotic.grind.internal.api.model.DefaultJobDefinition;
import org.kinotic.grind.internal.api.services.TaskClassCompiler;

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
     * The version of this job's tasks, recorded with every execution. A run recorded with a
     * version is resumed only by a definition of that version.
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
     * True if one run of this job executes at a time across the cluster for an owner.
     * @return true when exclusive
     */
    boolean isExclusive();

    /**
     * Sets the stable name identifying this job across runs.
     * @param name to use
     * @return this for fluent use
     */
    JobDefinition name(String name);

    /**
     * Sets the version of this job's tasks. Change it whenever the tasks, their order or what
     * they store changes: {@link JobService#resume(String, JobDefinition)} refuses a run
     * recorded with another version, since that run's task records would not line up with
     * this definition's tasks.
     * @param version to use
     * @return this for fluent use
     */
    JobDefinition version(String version);

    /**
     * Lets one run of this job execute at a time across the cluster for an owner. A run or
     * resume of the job's name started for an owner while another executes for that owner is
     * refused before anything of it is recorded: its events fail with an
     * {@link IllegalStateException}.
     * @return this for fluent use
     */
    JobDefinition exclusive();

    /**
     * Seeds the given values into the job scope before the first task runs, each stored as a
     * bean so tasks can inject it by type.
     * @param values to seed
     * @return this for fluent use
     */
    JobDefinition input(Object... values);

    /**
     * Adds a {@link Task}, its result kept as the task's {@link Task#getStore() Store} declares, or
     * not kept when it declares none.
     * @param task to add
     * @return this for fluent use
     */
    JobDefinition task(Task<?> task);

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
