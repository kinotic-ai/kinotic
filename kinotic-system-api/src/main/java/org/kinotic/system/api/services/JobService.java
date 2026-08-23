

package org.kinotic.system.api.services;

import org.kinotic.system.api.model.grind.JobDefinition;
import org.kinotic.system.api.model.grind.JobExecution;
import org.kinotic.domain.api.model.grind.JobOwner;
import org.kinotic.system.api.model.grind.Result;
import org.kinotic.system.api.model.grind.ResultOptions;
import org.kinotic.system.api.model.grind.ResultType;
import org.kinotic.system.api.model.grind.Task;
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

    /**
     * Prepares a recorded execution of the given {@link JobDefinition} on behalf of the given
     * {@link JobOwner}, recorded on the run so runs can be filtered by owner. The definition
     * itself stays a system-wide template - ownership belongs to this execution
     * @param jobDefinition to execute, its {@link JobDefinition#getName()} must be set
     * @param owner the hierarchy this run executes on behalf of, or null for a platform run
     * @return the prepared {@link JobExecution}
     */
    JobExecution execute(JobDefinition jobDefinition, JobOwner owner);

    /**
     * Prepares a recorded execution of the given {@link JobDefinition} on behalf of the given
     * {@link JobOwner}, as {@link #execute(JobDefinition, JobOwner)}, with the given {@link ResultOptions}
     * @param jobDefinition to execute, its {@link JobDefinition#getName()} must be set
     * @param owner the hierarchy this run executes on behalf of, or null for a platform run
     * @param options the {@link ResultOptions} to use when executing
     * @return the prepared {@link JobExecution}
     */
    JobExecution execute(JobDefinition jobDefinition, JobOwner owner, ResultOptions options);

    /**
     * Prepares a recorded execution that resumes a previous run: steps the original run completed
     * are not executed again. A completed {@code taskStoreState} step replays its recorded value, a
     * completed {@code taskStoreResult} step re-runs (or runs its reload task when one was declared),
     * and a completed step that stored nothing is skipped. Everything else executes normally.
     * The resume is recorded as a new {@link org.kinotic.domain.api.model.grind.JobRun} referencing
     * the original via {@code resumedFrom}, owned by the original run's owner.
     *
     * The given {@link JobDefinition} must be freshly built by the same code that built the original
     * run's definition: its name and version must match the recorded run, and its step structure must
     * be unchanged, or replayed steps will not line up with their records
     * @param jobRunId the id of the FAILED or CANCELLED run to resume
     * @param jobDefinition the freshly built definition to execute
     * @return the prepared {@link JobExecution}
     */
    JobExecution resume(String jobRunId, JobDefinition jobDefinition);

    /**
     * Prepares a recorded execution that resumes a previous run, as {@link #resume(String, JobDefinition)},
     * with the given {@link ResultOptions}
     * @param jobRunId the id of the FAILED or CANCELLED run to resume
     * @param jobDefinition the freshly built definition to execute
     * @param options the {@link ResultOptions} to use when executing
     * @return the prepared {@link JobExecution}
     */
    JobExecution resume(String jobRunId, JobDefinition jobDefinition, ResultOptions options);

    /**
     * Opens a view of a run currently executing in this process. The returned {@link Flux} replays
     * every {@link Result} emitted since the run started, then continues live until the run
     * terminates. Watching never starts a run - subscribing attaches to the in-flight execution only.
     * <p>
     * Result values are reduced to what a monitoring caller can consume remotely:
     * {@link ResultType#DYNAMIC_STEPS} carries the discovered steps as PENDING
     * {@link org.kinotic.domain.api.model.grind.TaskRecord}s in discovery order,
     * {@link ResultType#STEP_FAILED} carries the failure message, and
     * {@link ResultType#STEP_COMPLETED} and {@link ResultType#VALUE} carry no produced value.
     * @param jobRunId the id of the run to watch
     * @return the run's {@link Result} stream, or an empty {@link Flux} when no run with the
     *         given id is executing in this process
     */
    Flux<Result<?>> watchExecution(String jobRunId);

}
