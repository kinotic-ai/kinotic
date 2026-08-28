package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.system.api.model.grind.JobOwner;
import org.kinotic.system.api.model.grind.JobRun;
import org.kinotic.domain.internal.api.services.AbstractCrudService;
import org.kinotic.system.api.services.JobRunService;
import org.kinotic.system.internal.api.repositories.JobRunRepository;
import org.springframework.stereotype.Component;

@Component
public class DefaultJobRunService extends AbstractCrudService<JobRun> implements JobRunService {

    private final JobRunRepository jobRunRepository;

    public DefaultJobRunService(JobRunRepository repository) {
        super(repository);
        this.jobRunRepository = repository;
    }

    @Override
    public Future<Page<JobRun>> findByName(String name, Pageable pageable) {
        Validate.notBlank(name, "name cannot be blank");
        return jobRunRepository.findByName(name, pageable);
    }

    @Override
    public Future<Page<JobRun>> findAllForOwner(JobOwner owner, Pageable pageable) {
        Validate.notNull(owner, "owner cannot be null");
        return jobRunRepository.findAllForOwner(owner, pageable);
    }

    @Override
    protected Future<Void> beforeSave(JobRun entity) {
        Validate.notNull(entity, "JobRun cannot be null");
        Validate.notNull(entity.getId(), "JobRun id cannot be null");
        Validate.notNull(entity.getStatus(), "JobRun status cannot be null");
        Validate.isTrue(entity.getApplicationId() == null || entity.getOrganizationId() != null,
                        "JobRun applicationId requires organizationId");
        Validate.isTrue(entity.getProjectId() == null || entity.getOrganizationId() != null,
                        "JobRun projectId requires organizationId");
        return Future.succeededFuture();
    }

}
