package org.kinotic.grind.internal.api.repositories;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.TaskRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for the run ledger: each {@link JobRun} and its per-task {@link TaskRecord}s.
 * The engine writes through it as a run executes and reads it back to resume a failed run.
 * Runs live in the {@code kinotic_job_run} index this repository owns, their task records in
 * the {@link TaskRecordRepository}'s.
 */
@Component
public class JobRunRepository extends AbstractRepository<JobRun> {

    private static final int RECORD_PAGE_SIZE = 500;

    private final TaskRecordRepository taskRecordRepository;

    public JobRunRepository(CrudServiceTemplate crudServiceTemplate,
                            TaskRecordRepository taskRecordRepository) {
        super("kinotic_job_run", JobRun.class, crudServiceTemplate);
        this.taskRecordRepository = taskRecordRepository;
    }

    /**
     * Saves the given run, creating or updating its record.
     * @param jobRun the run to save
     * @return a future that will complete with the saved run
     */
    public Future<JobRun> saveRun(JobRun jobRun) {
        return save(jobRun);
    }

    /**
     * Saves the given task record, creating or updating it.
     * @param taskRecord the record to save
     * @return a future that will complete with the saved record
     */
    public Future<TaskRecord> saveTask(TaskRecord taskRecord) {
        return taskRecordRepository.save(taskRecord);
    }

    /**
     * Finds a run by id.
     * @param jobRunId the id of the run
     * @return a future that will complete with the run, or {@code null} when none exists
     */
    public Future<JobRun> findRun(String jobRunId) {
        return findById(jobRunId);
    }

    /**
     * Finds every task record of the given run.
     * @param jobRunId the id of the run
     * @return a future that will complete with the run's records
     */
    public Future<List<TaskRecord>> findTasks(String jobRunId) {
        List<TaskRecord> collected = new ArrayList<>();
        return readTaskPage(jobRunId, 0, collected).map(collected);
    }

    /**
     * Returns the page of runs owned by the given {@link JobOwner}: all of an organization's
     * runs, narrowed to an application and/or project when the owner carries those ids. The
     * system owner selects platform runs - those owned by no organization.
     */
    public Future<Page<JobRun>> findAllForOwner(JobOwner owner, Pageable pageable) {
        return findAll(pageable, b -> b.query(composeFilter(
            owner.getOrganizationId() != null ? termFilter("organizationId", owner.getOrganizationId())
                                              : missingFilter("organizationId"),
            owner.getApplicationId() != null ? termFilter("applicationId", owner.getApplicationId()) : null,
            owner.getProjectId() != null ? termFilter("projectId", owner.getProjectId()) : null)));
    }

    private Future<Void> readTaskPage(String jobRunId, int pageNumber, List<TaskRecord> collected) {
        return taskRecordRepository.findAllForJobRun(jobRunId, Pageable.create(pageNumber, RECORD_PAGE_SIZE, null))
                                   .compose(page -> {
                                       collected.addAll(page.getContent());
                                       Future<Void> ret;
                                       if(page.getContent().size() < RECORD_PAGE_SIZE){
                                           ret = Future.succeededFuture();
                                       }else{
                                           ret = readTaskPage(jobRunId, pageNumber + 1, collected);
                                       }
                                       return ret;
                                   });
    }

}
