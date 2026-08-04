

package org.kinotic.orchestrator.api.grind;

import reactor.core.publisher.Flux;

/**
 *
 * Created by Navid Mitchell on 3/19/20
 */
public interface JobService {

    /**
     * Takes the given {@link JobDefinition} and assembles a {@link Flux} that when subscribed to will execute all of the {@link Task}'s within the {@link JobDefinition}
     * @param jobDefinition to assemble
     * @return the {@link Flux} that will execute the {@link JobDefinition}
     */
    Flux<Result<?>> assemble(JobDefinition jobDefinition);

    /**
     * Takes the given {@link JobDefinition} and assembles a {@link Flux} that when subscribed to will execute all of the {@link Task}'s within the {@link JobDefinition}
     * @param jobDefinition to assemble
     * @param options the {@link ResultOptions} to use when executing the {@link JobDefinition}
     *               this will determine the {@link ResultType}'s that you will receive from the emitted {@link Result}'s
     * @return the {@link Flux} that will execute the {@link JobDefinition}
     */
    Flux<Result<?>> assemble(JobDefinition jobDefinition, ResultOptions options);

    /**
     * Prepares a recorded execution of the given {@link JobDefinition}, persisting a
     * {@link org.kinotic.domain.api.model.grind.JobRun} for the run and a
     * {@link org.kinotic.domain.api.model.grind.TaskRecord} for every step executed.
     * The run starts, and its records are written, when the returned
     * {@link JobExecution#getResults()} is subscribed to.
     * @param jobDefinition to execute, its {@link JobDefinition#getName()} must be set
     * @return the prepared {@link JobExecution}
     */
    JobExecution execute(JobDefinition jobDefinition);

    /**
     * Prepares a recorded execution of the given {@link JobDefinition}, persisting a
     * {@link org.kinotic.domain.api.model.grind.JobRun} for the run and a
     * {@link org.kinotic.domain.api.model.grind.TaskRecord} for every step executed.
     * The run starts, and its records are written, when the returned
     * {@link JobExecution#getResults()} is subscribed to.
     * @param jobDefinition to execute, its {@link JobDefinition#getName()} must be set
     * @param options the {@link ResultOptions} to use when executing the {@link JobDefinition}
     * @return the prepared {@link JobExecution}
     */
    JobExecution execute(JobDefinition jobDefinition, ResultOptions options);

}
