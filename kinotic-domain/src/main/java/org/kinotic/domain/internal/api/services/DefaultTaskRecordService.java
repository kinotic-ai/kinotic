package org.kinotic.domain.internal.api.services;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.grind.TaskRecord;
import org.kinotic.domain.api.services.TaskRecordService;
import org.kinotic.domain.internal.api.repositories.TaskRecordRepository;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class DefaultTaskRecordService extends AbstractCrudService<TaskRecord> implements TaskRecordService {

    private final TaskRecordRepository taskRecordRepository;

    public DefaultTaskRecordService(TaskRecordRepository repository) {
        super(repository);
        this.taskRecordRepository = repository;
    }

    @Override
    public CompletableFuture<Page<TaskRecord>> findAllForJobRun(String jobRunId, Pageable pageable) {
        Validate.notBlank(jobRunId, "jobRunId cannot be blank");
        return taskRecordRepository.findAllForJobRun(jobRunId, pageable);
    }

    @Override
    protected CompletableFuture<Void> beforeSave(TaskRecord entity) {
        Validate.notNull(entity, "TaskRecord cannot be null");
        Validate.notNull(entity.getId(), "TaskRecord id cannot be null");
        Validate.notNull(entity.getJobRunId(), "TaskRecord jobRunId cannot be null");
        Validate.notNull(entity.getStepPath(), "TaskRecord stepPath cannot be null");
        Validate.notNull(entity.getStatus(), "TaskRecord status cannot be null");
        return CompletableFuture.completedFuture(null);
    }

}
