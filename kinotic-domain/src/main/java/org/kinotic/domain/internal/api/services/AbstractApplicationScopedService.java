package org.kinotic.domain.internal.api.services;

import org.kinotic.core.api.crud.ApplicationScopedCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.ApplicationScoped;
import org.kinotic.domain.internal.api.repositories.AbstractApplicationScopedRepository;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractApplicationScopedService<T extends ApplicationScoped<String>>
        extends AbstractOrganizationScopedService<T>
        implements ApplicationScopedCrudService<T, String> {

    protected final AbstractApplicationScopedRepository<T> applicationRepository;

    public AbstractApplicationScopedService(AbstractApplicationScopedRepository<T> repository,
                                            SecurityContext securityContext) {
        super(repository, securityContext);
        this.applicationRepository = repository;
    }

    @Override
    public CompletableFuture<Long> countForApplication(String applicationId) {
        String orgId = getOrganizationIdIfEnforced();
        return orgId != null
                ? applicationRepository.countForApplication(applicationId, orgId)
                : applicationRepository.countForApplication(applicationId);
    }

    @Override
    public CompletableFuture<Page<T>> findAllForApplication(String applicationId, Pageable pageable) {
        String orgId = getOrganizationIdIfEnforced();
        return orgId != null
                ? applicationRepository.findAllForApplication(applicationId, pageable, orgId)
                : applicationRepository.findAllForApplication(applicationId, pageable);
    }

}
