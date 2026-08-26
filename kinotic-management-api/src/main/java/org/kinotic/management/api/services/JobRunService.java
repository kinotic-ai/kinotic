package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.management.api.model.grind.JobOwner;
import org.kinotic.management.api.model.grind.JobRun;
import org.kinotic.management.api.model.grind.StepRecord;

/**
 * The persistent record of grind job executions: each {@link JobRun} and its per-step
 * {@link StepRecord} ledger. The grind engine writes through this service as a run
 * executes; readers see a run's history whether it is live or long finished.
 */
public interface JobRunService {

    /**
     * Saves the given run, creating or updating its record.
     * @param jobRun the run to save; its id, status, and owner scope must be set
     * @return a future that will complete with the saved run
     */
    Future<JobRun> save(JobRun jobRun);

    /**
     * Finds a run by id.
     * @param jobRunId the id of the run
     * @return a future that will complete with the run, or {@code null} when none exists
     */
    Future<JobRun> findById(String jobRunId);

    /**
     * Finds every recorded run.
     * @param pageable the page of runs to return
     * @return a future that will complete with the page of runs
     */
    Future<Page<JobRun>> findAll(Pageable pageable);

    /**
     * Finds the runs owned by the given {@link JobOwner}: all of an organization's runs,
     * narrowed to an application and/or project when the owner carries those ids, or the
     * platform's runs for {@link JobOwner#system()}.
     * @param owner whose runs to find
     * @param pageable the page of runs to return
     * @return a future that will complete with the page of runs
     */
    Future<Page<JobRun>> findAllForOwner(JobOwner owner, Pageable pageable);

    /**
     * Saves the given step record, creating or updating it.
     * @param stepRecord the record to save; its id, jobRunId, stepPath, and status must be set
     * @return a future that will complete with the saved record
     */
    Future<StepRecord> saveStep(StepRecord stepRecord);

    /**
     * Finds the step records of the given run.
     * @param jobRunId the id of the run
     * @param pageable the page of records to return
     * @return a future that will complete with the page of records
     */
    Future<Page<StepRecord>> findSteps(String jobRunId, Pageable pageable);

}
