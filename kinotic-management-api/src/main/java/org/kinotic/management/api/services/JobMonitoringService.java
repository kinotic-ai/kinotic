package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Scope;
import org.kinotic.core.api.annotations.ScopeOptional;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.TaskRecord;
import org.kinotic.grind.api.model.events.JobRunEvent;
import org.kinotic.grind.api.model.events.TaskCompletedEvent;
import reactor.core.publisher.Flux;

/**
 * Access to grind job runs for the authenticated participant: an organization or application
 * participant sees the runs its organization owns, a system participant sees every run. A
 * run's {@link TaskRecord}s are its task ledger - every discovered task has a record, PENDING
 * until it starts executing.
 *
 * Each node publishes its own instance, scoped by {@link #nodeId()}. The finders read shared
 * state, so any instance answers them; {@link #watch(String)} serves a run's live event
 * stream, which exists only in the executing process, so it is invoked with the scope
 * recorded on {@link JobRun#getNodeId()}.
 */
@Publish
public interface JobMonitoringService {

    /**
     * The id of the node this instance serves, identifying it as the service's {@link Scope}.
     * @return the node id
     */
    @Scope
    String nodeId();

    /**
     * Finds the job runs the participant may view.
     *
     * @param pageable the page of runs to return
     * @return a future that will complete with the page of runs
     */
    @ScopeOptional
    Future<Page<JobRun>> findJobRuns(Pageable pageable);

    /**
     * Finds a single job run the participant may view.
     *
     * @param jobRunId the id of the run
     * @return a future that will complete with the run, or fail if the run does not exist
     *         or belongs to another organization
     */
    @ScopeOptional
    Future<JobRun> findJobRun(String jobRunId);

    /**
     * Finds the task ledger of a job run the participant may view.
     *
     * @param jobRunId the id of the run
     * @param pageable the page of records to return
     * @return a future that will complete with the page of records, or fail if the run does
     *         not exist or belongs to another organization
     */
    @ScopeOptional
    Future<Page<TaskRecord>> findTasks(String jobRunId, Pageable pageable);

    /**
     * Opens a live view of a job run the participant may view, replaying every
     * {@link JobRunEvent} emitted since the run started and continuing until the run
     * terminates. A {@link TaskCompletedEvent} carries only its {@code wireValue} - the live
     * {@code storedValue} stays in the executing process.
     *
     * @param jobRunId the id of the run to watch
     * @return the run's event stream, empty when the run is not currently executing on this
     *         node
     */
    Flux<JobRunEvent> watch(String jobRunId);

}
