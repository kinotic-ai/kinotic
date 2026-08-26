package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.management.api.model.grind.JobOwner;
import org.kinotic.management.api.model.grind.JobRun;
import org.kinotic.management.api.model.grind.StepRecord;
import org.kinotic.management.api.services.JobRunService;
import org.kinotic.management.internal.api.repositories.JobRunRepository;
import org.kinotic.management.internal.api.repositories.StepRecordRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultJobRunService implements JobRunService {

    private final JobRunRepository jobRunRepository;
    private final StepRecordRepository stepRecordRepository;

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
    public Future<StepRecord> saveStep(StepRecord stepRecord) {
        Validate.notNull(stepRecord, "StepRecord cannot be null");
        Validate.notNull(stepRecord.getId(), "StepRecord id cannot be null");
        Validate.notNull(stepRecord.getJobRunId(), "StepRecord jobRunId cannot be null");
        Validate.notNull(stepRecord.getStepPath(), "StepRecord stepPath cannot be null");
        Validate.notNull(stepRecord.getStatus(), "StepRecord status cannot be null");
        return stepRecordRepository.save(stepRecord);
    }

    @Override
    public Future<Page<StepRecord>> findSteps(String jobRunId, Pageable pageable) {
        Validate.notBlank(jobRunId, "jobRunId cannot be blank");
        return stepRecordRepository.findAllForJobRun(jobRunId, pageable);
    }

}
