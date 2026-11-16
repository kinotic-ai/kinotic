package org.kinotic.grind.api.services;

import org.apache.ignite.cluster.ClusterGroup;
import org.kinotic.domain.api.model.WatchedParent;
import org.kinotic.grind.api.model.*;
import org.kinotic.grind.api.model.events.JobRunEvent;
import reactor.core.publisher.Flux;

import java.util.List;


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
     *              for a system run
     * @return the prepared {@link JobRunHandle}
     * @throws IllegalStateException when called by a task of a run: a task that has more work
     *         returns it as a {@link Task} or {@link JobDefinition}, which executes as a step of
     *         the task's own run
     */
    JobRunHandle run(JobDefinition jobDefinition, JobOwner owner);

    /**
     * As {@link #run(JobDefinition, JobOwner)}, recording what the run was made by, so a change to
     * the run reaches that record's worker.
     * @param jobDefinition to execute, its {@link JobDefinition#getName()} must be set
     * @param owner the hierarchy this run executes on behalf of
     * @param parent the record whose worker made the run
     * @return the prepared {@link JobRunHandle}
     * @throws IllegalStateException when called by a task of a run
     */
    JobRunHandle run(JobDefinition jobDefinition, JobOwner owner, WatchedParent parent);

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
     * @throws IllegalStateException when called by a task of a run
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

    /**
     * Lists this node's {@link SystemJob}s.
     * @return a fresh definition of each system job, ordered by name
     */
    List<JobDefinition> findSystemJobs();

    /**
     * Prepares a recorded execution of the named {@link SystemJob} on behalf of the system, as
     * {@link #run(JobDefinition, JobOwner)} does for a fresh definition of it.
     * @param name the name of the system job to run
     * @return the prepared {@link JobRunHandle}
     * @throws IllegalArgumentException when this node has no system job of the given name
     * @throws IllegalStateException when called by a task of a run
     */
    JobRunHandle runSystemJob(String name);

    /**
     * Requests the cluster's job manager: one singleton, hosted by one of the given nodes, that has a
     * run of each {@link SystemJob} whose definition declares a {@link JobDefinition#getPeriod() period}
     * started on one of those nodes whenever a period has passed since the job's latest run started.
     * Every node that holds the system jobs requests it, and the cluster keeps one.
     * @param hosts the nodes that hold the system jobs
     */
    void deployJobManager(ClusterGroup hosts);

}
