package org.kinotic.domain.internal.api.services;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.ProjectScopedCrudService;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.ProjectScoped;
import org.kinotic.domain.internal.api.repositories.AbstractProjectRepository;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractProjectCrudService<T extends ProjectScoped<String>>
        extends AbstractApplicationCrudService<T>
        implements ProjectScopedCrudService<T, String> {

    protected final AbstractProjectRepository<T> projectRepository;

    public AbstractProjectCrudService(AbstractProjectRepository<T> repository,
                                      SecurityContext securityContext) {
        super(repository, securityContext);
        this.projectRepository = repository;
    }

    @Override
    protected String getRoutingKeyFromId(String id) {
        if (id != null) {
            int dotIndex = id.indexOf('.');
            if (dotIndex > 0) {
                return id.substring(0, dotIndex);
            }
        }
        return null;
    }

    @Override
    public CompletableFuture<Long> countForProject(String projectId) {
        return projectRepository.countForProject(projectId, getOrganizationIdIfEnforced());
    }

    @Override
    public CompletableFuture<Page<T>> findAllForProject(String projectId, Pageable pageable) {
        return projectRepository.findAllForProject(projectId, pageable, getOrganizationIdIfEnforced());
    }

}
