package org.kinotic.domain.internal.api.services;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
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
    public CompletableFuture<Long> countForProject(String projectId) {
        String orgId = getOrganizationIdIfEnforced();
        if (orgId == null) {
            return projectRepository.countForProject(projectId);
        }
        Query composed = projectRepository.buildProjectQuery(projectId, buildOrgFilterQuery(orgId));
        return projectRepository.countForProject(projectId, b -> b.routing(orgId).query(composed));
    }

    @Override
    public CompletableFuture<Page<T>> findAllForProject(String projectId, Pageable pageable) {
        String orgId = getOrganizationIdIfEnforced();
        if (orgId == null) {
            return projectRepository.findAllForProject(projectId, pageable);
        }
        Query composed = projectRepository.buildProjectQuery(projectId, buildOrgFilterQuery(orgId));
        return projectRepository.findAllForProject(projectId, pageable, b -> b.routing(orgId).query(composed));
    }

}
