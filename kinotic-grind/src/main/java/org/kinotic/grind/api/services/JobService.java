package org.kinotic.grind.api.services;

import org.kinotic.grind.api.model.*;
import org.kinotic.grind.api.model.events.JobRunEvent;
import reactor.core.publisher.Flux;


/**
 * Executes {@link JobDefinition}s as recorded runs: a {@link JobRun} is persisted for the run
 * and a {@link TaskRecord} for every task. Records are written ahead of execution - the run
 * and its PENDING task records before the first task starts, a task's RUNNING record before
 * the task executes, and its terminal record before the next task starts - so a run cut short
 * by its node leaves a ledger naming the task that was in flight.
 */
public interface JobService {

    /**
     * Prepares a recorded execution of the given {@link JobDefinition} on behalf of the given
     * {@link JobOwner}. The run starts, and its records are written, when the returned
     * handle's events are first subscribed - {@link JobRunHandle#completion()} subscribes.
     * @param jobDefinition to execute, its {@link JobDefinition#getName()} must be set
     * @param owner the hierarchy this run executes on behalf of, {@link JobOwner#system()}
     *              for a platform run
     * @return the prepared {@link JobRunHandle}
     */
    JobRunHandle run(JobDefinition jobDefinition, JobOwner owner);

    /**
     * Prepares a recorded execution that resumes a previous run: tasks the original run
     * completed are not executed again, according to each task's {@link StoreType}. The
     * resume is recorded as a new {@link JobRun} referencing the original, owned by the
     * original run's owner.
     *
     * The given {@link JobDefinition} must be freshly built by the same code that built the
     * original run's definition: its name and version must match the recorded run, and its
     * task structure must be unchanged, or replayed tasks will not line up with their records.
     * @param jobRunId the id of the FAILED or CANCELLED run to resume
     * @param jobDefinition the freshly built definition to execute
     * @return the prepared {@link JobRunHandle}
     */
    JobRunHandle resume(String jobRunId, JobDefinition jobDefinition);

    /**
     * Opens a view of a run currently executing in this process. The returned {@link Flux}
     * replays every {@link JobRunEvent} emitted since the run started, then continues live
     * until the run terminates. Watching never starts a run - subscribing attaches to the
     * in-flight execution only.
     * @param jobRunId the id of the run to watch
     * @return the run's event stream, or an empty {@link Flux} when no run with the given id
     *         is executing in this process
     */
    Flux<JobRunEvent> watchRun(String jobRunId);

}
