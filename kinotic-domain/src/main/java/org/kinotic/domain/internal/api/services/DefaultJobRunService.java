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
    public CompletableFuture<Page<JobRun>> findByName(String name, Pageable pageable) {
        Validate.notBlank(name, "name cannot be blank");
        return jobRunRepository.findByName(name, pageable);
    }

    @Override
    public CompletableFuture<Page<JobRun>> findAllForOwner(String organizationId,
                                                           String applicationId,
                                                           String projectId,
                                                           Pageable pageable) {
        Validate.isTrue(organizationId != null || (applicationId == null && projectId == null),
                        "applicationId and projectId require an organizationId");
        return jobRunRepository.findAllForOwner(organizationId, applicationId, projectId, pageable);
    }

    @Override
    protected CompletableFuture<Void> beforeSave(JobRun entity) {
        Validate.notNull(entity, "JobRun cannot be null");
        Validate.notNull(entity.getId(), "JobRun id cannot be null");
        Validate.notNull(entity.getStatus(), "JobRun status cannot be null");
        Validate.isTrue(entity.getApplicationId() == null || entity.getOrganizationId() != null,
                        "JobRun applicationId requires organizationId");
        Validate.isTrue(entity.getProjectId() == null || entity.getOrganizationId() != null,
                        "JobRun projectId requires organizationId");
        return CompletableFuture.completedFuture(null);
    }

}
