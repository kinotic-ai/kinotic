package org.kinotic.domain.internal.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.ProjectScoped;
import org.kinotic.domain.api.services.ProjectScopedCrudService;
import org.kinotic.domain.internal.api.repositories.AbstractProjectScopedRepository;

public abstract class AbstractProjectScopedService<T extends ProjectScoped<String>>
        extends AbstractApplicationScopedService<T>
        implements ProjectScopedCrudService<T, String> {

    protected final AbstractProjectScopedRepository<T> projectRepository;

    public AbstractProjectScopedService(AbstractProjectScopedRepository<T> repository,
                                        SecurityContext securityContext) {
        super(repository, securityContext);
        this.projectRepository = repository;
    }

    @Override
    public Future<Long> countForProject(String projectId) {
        return projectRepository.countForProject(projectId, requireOrganizationId());
    }

    @Override
    public Future<Page<T>> findAllForProject(String projectId, Pageable pageable) {
        return projectRepository.findAllForProject(projectId, requireOrganizationId(), pageable);
    }

}
