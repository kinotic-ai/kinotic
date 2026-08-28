package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.TaskRecord;

/**
 * Read access to grind job runs for the authenticated participant: an organization or
 * application participant sees the runs its organization owns, a system participant sees every
 * run. A run's {@link TaskRecord}s are its task ledger - every discovered task has a record,
 * PENDING until it starts executing. The live event stream of a run is served by
 * {@link JobWatchService} on the node named by {@link JobRun#getNodeId()}.
 */
@Publish
public interface JobMonitoringService {

    /**
     * Finds the job runs the participant may view.
     *
     * @param pageable the page of runs to return
     * @return a future that will complete with the page of runs
     */
    Future<Page<JobRun>> findJobRuns(Pageable pageable);

    /**
     * Finds a single job run the participant may view.
     *
     * @param jobRunId the id of the run
     * @return a future that will complete with the run, or fail if the run does not exist
     *         or belongs to another organization
     */
    Future<JobRun> findJobRun(String jobRunId);

    /**
     * Finds the task ledger of a job run the participant may view.
     *
     * @param jobRunId the id of the run
     * @param pageable the page of records to return
     * @return a future that will complete with the page of records, or fail if the run does
     *         not exist or belongs to another organization
     */
    Future<Page<TaskRecord>> findTasks(String jobRunId, Pageable pageable);

}
