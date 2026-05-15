package org.kinotic.domain.internal.api.services;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.kinotic.core.api.crud.ApplicationScopedCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.ApplicationScoped;
import org.kinotic.domain.internal.api.repositories.AbstractApplicationRepository;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractApplicationCrudService<T extends ApplicationScoped<String>>
        extends AbstractCrudService<T>
        implements ApplicationScopedCrudService<T, String> {

    protected final AbstractApplicationRepository<T> applicationRepository;

    public AbstractApplicationCrudService(AbstractApplicationRepository<T> repository,
                                          SecurityContext securityContext) {
        super(repository, securityContext);
        this.applicationRepository = repository;
    }

    @Override
    public CompletableFuture<Long> countForApplication(String applicationId) {
        String orgId = getOrganizationIdIfEnforced();
        if (orgId == null) {
            return applicationRepository.countForApplication(applicationId);
        }
        Query composed = applicationRepository.buildApplicationQuery(applicationId, buildOrgFilterQuery(orgId));
        return applicationRepository.countForApplication(applicationId, b -> b.routing(orgId).query(composed));
    }

    @Override
    public CompletableFuture<Page<T>> findAllForApplication(String applicationId, Pageable pageable) {
        String orgId = getOrganizationIdIfEnforced();
        if (orgId == null) {
            return applicationRepository.findAllForApplication(applicationId, pageable);
        }
        Query composed = applicationRepository.buildApplicationQuery(applicationId, buildOrgFilterQuery(orgId));
        return applicationRepository.findAllForApplication(applicationId, pageable, b -> b.routing(orgId).query(composed));
    }

}
