package org.kinotic.grind.internal.api.repositories;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.TaskRecord;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch-backed {@link JobRunRepository}: runs live in the {@code kinotic_job_run}
 * index this repository owns, their task records in the {@link TaskRecordRepository}'s.
 */
@Component
public class DefaultJobRunRepository extends AbstractRepository<JobRun> implements JobRunRepository {

    private static final int RECORD_PAGE_SIZE = 500;

    private final TaskRecordRepository taskRecordRepository;

    public DefaultJobRunRepository(CrudServiceTemplate crudServiceTemplate,
                                   TaskRecordRepository taskRecordRepository) {
        super("kinotic_job_run", JobRun.class, crudServiceTemplate);
        this.taskRecordRepository = taskRecordRepository;
    }

    @Override
    public Future<JobRun> saveRun(JobRun jobRun) {
        return save(jobRun);
    }

    @Override
    public Future<TaskRecord> saveTask(TaskRecord taskRecord) {
        return taskRecordRepository.save(taskRecord);
    }

    @Override
    public Future<JobRun> findRun(String jobRunId) {
        return findById(jobRunId);
    }

    @Override
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
