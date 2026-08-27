package org.kinotic.grind.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.TaskRecord;
import org.kinotic.grind.internal.api.repositories.JobRunRepository;
import org.kinotic.grind.internal.api.repositories.TaskRecordRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultJobRunService implements JobRunService {

    private final JobRunRepository jobRunRepository;
    private final TaskRecordRepository taskRecordRepository;

    @Override
    public Future<JobRun> save(JobRun jobRun) {
        Validate.notNull(jobRun, "JobRun cannot be null");
        Validate.notNull(jobRun.getId(), "JobRun id cannot be null");
        Validate.notNull(jobRun.getStatus(), "JobRun status cannot be null");
        Validate.isTrue(jobRun.getApplicationId() == null || jobRun.getOrganizationId() != null,
                        "JobRun applicationId requires organizationId");
        Validate.isTrue(jobRun.getProjectId() == null || jobRun.getOrganizationId() != null,
                        "JobRun projectId requires organizationId");
        return jobRunRepository.save(jobRun);
    }

    @Override
    public Future<JobRun> findById(String jobRunId) {
        Validate.notBlank(jobRunId, "jobRunId cannot be blank");
        return jobRunRepository.findById(jobRunId);
    }

    @Override
    public Future<Page<JobRun>> findAll(Pageable pageable) {
        return jobRunRepository.findAll(pageable);
    }

    @Override
    public Future<Page<JobRun>> findAllForOwner(JobOwner owner, Pageable pageable) {
        Validate.notNull(owner, "owner cannot be null");
        return jobRunRepository.findAllForOwner(owner, pageable);
    }

    @Override
    public Future<TaskRecord> saveStep(TaskRecord taskRecord) {
        Validate.notNull(taskRecord, "TaskRecord cannot be null");
        Validate.notNull(taskRecord.getId(), "TaskRecord id cannot be null");
        Validate.notNull(taskRecord.getJobRunId(), "TaskRecord jobRunId cannot be null");
        Validate.notNull(taskRecord.getStepPath(), "TaskRecord stepPath cannot be null");
        Validate.notNull(taskRecord.getStatus(), "TaskRecord status cannot be null");
        return taskRecordRepository.save(taskRecord);
    }

    @Override
    public Future<Page<TaskRecord>> findSteps(String jobRunId, Pageable pageable) {
        Validate.notBlank(jobRunId, "jobRunId cannot be blank");
        return taskRecordRepository.findAllForJobRun(jobRunId, pageable);
    }

}
