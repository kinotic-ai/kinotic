package org.kinotic.persistence.internal.api.services;

import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.internal.api.services.AbstractProjectScopedService;
import org.kinotic.persistence.api.model.NamedQueriesDefinition;
import org.kinotic.persistence.api.services.NamedQueriesDefinitionService;
import org.kinotic.persistence.internal.api.repositories.NamedQueriesDefinitionRepository;
import org.kinotic.persistence.internal.cache.events.CacheEvictionEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 4/23/24.
 */
@Slf4j
@Component
public class DefaultNamedQueriesDefinitionService extends AbstractProjectScopedService<NamedQueriesDefinition> implements NamedQueriesDefinitionService {

    private final NamedQueriesDefinitionRepository namedQueriesRepository;
    private final ApplicationEventPublisher eventPublisher;

    public DefaultNamedQueriesDefinitionService(NamedQueriesDefinitionRepository repository,
                                                ApplicationEventPublisher eventPublisher,
                                                SecurityContext securityContext) {
        super(repository, securityContext);
        this.namedQueriesRepository = repository;
        this.eventPublisher = eventPublisher;
    }


    @Override
    public CompletableFuture<NamedQueriesDefinition> findByApplicationAndEntityDefinition(String applicationId, String entityDefinitionName) {
        return namedQueriesRepository.findByApplicationAndEntityDefinition(applicationId, entityDefinitionName, requireOrganizationId());
    }

    // Every mutation below uses the Refresh.WaitFor write/delete variant and evicts after
    // it completes. The cache loader reads through a search, so the mutation must be
    // searchable before the eviction fires — otherwise a reload between the eviction and
    // the index refresh re-caches the old row with no eviction left to clear it.

    @Override
    public CompletableFuture<NamedQueriesDefinition> save(NamedQueriesDefinition definition) {
        // TODO: preprocess queries to correct index name and add Metadata about query type to be used by other parts of the system
        //       The Query type information will speed up other areas the need this as well
        return saveSync(definition);
    }

    @Override
    public CompletableFuture<NamedQueriesDefinition> saveSync(NamedQueriesDefinition definition) {
        return super.saveSync(definition).thenApply(this::publishModifiedEvent);
    }

    @Override
    public CompletableFuture<NamedQueriesDefinition> create(NamedQueriesDefinition definition) {
        return createSync(definition);
    }

    @Override
    public CompletableFuture<NamedQueriesDefinition> createSync(NamedQueriesDefinition definition) {
        return super.createSync(definition).thenApply(this::publishModifiedEvent);
    }

    /** Evicts cached queries after a successful write; shared by every save/create path. */
    private NamedQueriesDefinition publishModifiedEvent(NamedQueriesDefinition definition) {
        this.eventPublisher.publishEvent(CacheEvictionEvent.localModifiedNamedQuery(definition.getOrganizationId(),
                                                                                    definition.getApplicationId(),
                                                                                    definition.getEntityDefinitionName(),
                                                                                    definition.getId()));
        return definition;
    }

    @Override
    public CompletableFuture<Void> deleteById(String id) {
        return deleteAndEvict(id);
    }

    @Override
    public CompletableFuture<Void> deleteByIdSync(String id) {
        return deleteAndEvict(id);
    }

    private CompletableFuture<Void> deleteAndEvict(String id) {
        return findById(id)
                .thenCompose(namedQuery -> {
                    if (namedQuery == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("NamedQuery cannot be found for id: " + id));
                    }
                    return super.deleteByIdSync(id)
                            .thenApply(v -> {
                                this.eventPublisher.publishEvent(
                                        CacheEvictionEvent.localDeletedNamedQuery(
                                                namedQuery.getOrganizationId(),
                                                namedQuery.getApplicationId(),
                                                namedQuery.getEntityDefinitionName(),
                                                namedQuery.getId()));
                                return null;
                            });
                });
    }

}
