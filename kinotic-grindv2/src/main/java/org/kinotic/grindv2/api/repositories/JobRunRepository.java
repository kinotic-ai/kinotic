package org.kinotic.grindv2.api.repositories;

import org.kinotic.grindv2.api.model.JobRun;
import org.kinotic.grindv2.api.model.TaskRecord;
import io.vertx.core.Future;

import java.util.List;

/**
 * Persistence port for the run ledger: each {@link JobRun} and its per-task
 * {@link TaskRecord}s. The engine writes through this port as a run executes and reads it
 * back to resume a failed run; deployments provide the backing implementation.
 */
public interface JobRunRepository {

    /**
     * Saves the given run, creating or updating its record.
     * @param jobRun the run to save
     * @return a future that will complete with the saved run
     */
    Future<JobRun> saveRun(JobRun jobRun);

    /**
     * Saves the given task record, creating or updating it.
     * @param taskRecord the record to save
     * @return a future that will complete with the saved record
     */
    Future<TaskRecord> saveTask(TaskRecord taskRecord);

    /**
     * Finds a run by id.
     * @param jobRunId the id of the run
     * @return a future that will complete with the run, or {@code null} when none exists
     */
    Future<JobRun> findRun(String jobRunId);

    /**
     * Finds every task record of the given run.
     * @param jobRunId the id of the run
     * @return a future that will complete with the run's records
     */
    Future<List<TaskRecord>> findTasks(String jobRunId);

}
