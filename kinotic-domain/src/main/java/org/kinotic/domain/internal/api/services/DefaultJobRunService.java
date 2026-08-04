package org.kinotic.domain.internal.api.services;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.grind.JobRun;
import org.kinotic.domain.api.services.JobRunService;
import org.kinotic.domain.internal.api.repositories.JobRunRepository;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class DefaultJobRunService extends AbstractCrudService<JobRun> implements JobRunService {

    private final JobRunRepository jobRunRepository;

    public DefaultJobRunService(JobRunRepository repository) {
        super(repository);
        this.jobRunRepository = repository;
    }

    @Override
    public CompletableFuture<Page<JobRun>> findByPipeline(String pipeline, Pageable pageable) {
        Validate.notBlank(pipeline, "pipeline cannot be blank");
        return jobRunRepository.findByPipeline(pipeline, pageable);
    }

    @Override
    protected CompletableFuture<Void> beforeSave(JobRun entity) {
        Validate.notNull(entity, "JobRun cannot be null");
        Validate.notNull(entity.getId(), "JobRun id cannot be null");
        Validate.notNull(entity.getStatus(), "JobRun status cannot be null");
        return CompletableFuture.completedFuture(null);
    }

}
