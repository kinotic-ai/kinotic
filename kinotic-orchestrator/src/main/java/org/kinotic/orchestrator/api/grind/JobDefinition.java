

package org.kinotic.orchestrator.api.grind;

import org.kinotic.orchestrator.internal.api.grind.DefaultJobDefinition;

import java.util.List;

/**
 * A {@link JobDefinition} provides a unit of work comprised of {@link Task}'s and other {@link JobDefinition}
 * For every {@link JobDefinition} a {@link JobContext} is provided,
 * to allow {@link Task}'s to automatically store and access data produced by {@link Task}'s
 *
 * A {@link JobDefinition} is one run's worth of bound steps: its {@link Task} instances receive
 * injection per execution, so build a fresh definition for each run - executing the same instance
 * concurrently is not supported.
 *
 * Created by Navid Mitchell on 3/19/20
 */
public interface JobDefinition extends HasSteps{

    /**
     * @return the description of this {@link JobDefinition}
     */
    String getDescription();

    /**
     * The stable name identifying this job across runs, recorded with every execution.
     * Required by {@link JobService#execute(JobDefinition)}, optional for definitions
     * that are only nested within or assembled by others.
     * @return the name or null if none was set
     */
    String getName();

    /**
     * The version of this job's definition, recorded with every execution so a persisted
     * run can be matched to the code that produced it.
     * @return the version or null if none was set
     */
    String getVersion();

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
