package org.kinotic.domain.internal.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.ApplicationScoped;
import org.kinotic.domain.api.services.ApplicationScopedCrudService;
import org.kinotic.domain.internal.api.repositories.AbstractApplicationScopedRepository;

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
    public Future<Long> countForApplication(String applicationId) {
        return applicationRepository.countForApplication(applicationId, requireOrganizationId());
    }

    @Override
    public Future<Page<T>> findAllForApplication(String applicationId, Pageable pageable) {
        return applicationRepository.findAllForApplication(applicationId, requireOrganizationId(), pageable);
    }

}
