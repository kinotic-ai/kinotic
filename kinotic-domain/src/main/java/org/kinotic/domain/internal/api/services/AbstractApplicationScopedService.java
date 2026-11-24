package org.kinotic.domain.internal.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.ApplicationScoped;
import org.kinotic.domain.api.services.ApplicationScopedCrudService;
import org.kinotic.domain.internal.api.repositories.AbstractApplicationScopedRepository;

import java.util.Collection;

public abstract class AbstractApplicationScopedService<T extends ApplicationScoped<String>>
        extends AbstractOrganizationScopedService<T>
        implements ApplicationScopedCrudService<T, String> {

    protected final AbstractApplicationScopedRepository<T> applicationRepository;

    public AbstractApplicationScopedService(AbstractApplicationScopedRepository<T> repository,
                                            SecurityContext securityContext) {
        super(repository, securityContext);
        this.applicationRepository = repository;
    }

    // A record's application is where its grants are inherited from, so a write naming another application would
    // move it out from under the grants the gateway checked
    @Override
    protected Future<Void> beforeWrite(T entity) {
        return entity.getId() == null
                ? Future.succeededFuture()
                : scopedRepository.findById(entity.getId(), requireOrganizationId()).map(stored -> {
                    if (stored != null && !stored.getApplicationId().equals(entity.getApplicationId())) {
                        throw new IllegalArgumentException("The %s '%s' belongs to application '%s'; a record cannot move to another application"
                                                                   .formatted(scopedRepository.getType().getSimpleName(), stored.getId(), stored.getApplicationId()));
                    }
                    return null;
                });
    }

    @Override
    public Future<Long> countForApplication(String applicationId) {
        String organizationId = requireOrganizationId();
        return visibleIdsWithin(applicationId).compose(ids -> applicationRepository.countForApplication(applicationId, organizationId, ids));
    }

    @Override
    public Future<Page<T>> findAllForApplication(String applicationId, Pageable pageable) {
        String organizationId = requireOrganizationId();
        return visibleIdsWithin(applicationId).compose(ids -> applicationRepository.findAllForApplication(applicationId, organizationId, ids, pageable));
    }

    /**
     * Hook naming the records of one application the caller may see, which {@link #countForApplication} and
     * {@link #findAllForApplication} are narrowed to. Override where what the caller holds on the application
     * decides it without naming each record.
     *
     * @param applicationId the application listed
     * @return a future emitting the ids of the records the caller may see, or null for every record of the
     *         application
     */
    protected Future<? extends Collection<String>> visibleIdsWithin(String applicationId) {
        return visibleIds();
    }

}
