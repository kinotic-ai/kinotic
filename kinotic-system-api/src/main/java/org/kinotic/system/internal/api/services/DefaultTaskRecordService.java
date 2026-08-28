package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.system.api.model.grind.TaskRecord;
import org.kinotic.domain.internal.api.services.AbstractCrudService;
import org.kinotic.system.api.services.TaskRecordService;
import org.kinotic.system.internal.api.repositories.TaskRecordRepository;
import org.springframework.stereotype.Component;

@Component
public class DefaultTaskRecordService extends AbstractCrudService<TaskRecord> implements TaskRecordService {

    private final TaskRecordRepository taskRecordRepository;

    public DefaultTaskRecordService(TaskRecordRepository repository) {
        super(repository);
        this.taskRecordRepository = repository;
    }

    @Override
    public Future<Page<TaskRecord>> findAllForJobRun(String jobRunId, Pageable pageable) {
        Validate.notBlank(jobRunId, "jobRunId cannot be blank");
        return taskRecordRepository.findAllForJobRun(jobRunId, pageable);
    }

    @Override
    protected Future<Void> beforeSave(TaskRecord entity) {
        Validate.notNull(entity, "TaskRecord cannot be null");
        Validate.notNull(entity.getId(), "TaskRecord id cannot be null");
        Validate.notNull(entity.getJobRunId(), "TaskRecord jobRunId cannot be null");
        Validate.notNull(entity.getStepPath(), "TaskRecord stepPath cannot be null");
        Validate.notNull(entity.getStatus(), "TaskRecord status cannot be null");
        return Future.succeededFuture();
    }

}
