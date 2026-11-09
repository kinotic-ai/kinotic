package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.internal.api.services.AbstractProjectScopedService;
import org.kinotic.domain.api.model.persistence.NamedQueriesDefinition;
import org.kinotic.management.api.services.NamedQueriesDefinitionService;
import org.kinotic.domain.api.repositories.NamedQueriesDefinitionRepository;
import org.kinotic.core.api.event.EventService;
import org.kinotic.domain.api.cache.CacheEvictionEvent;
import org.kinotic.domain.api.cache.CacheEvictionException;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

/**
 * Created by Navíd Mitchell 🤪 on 4/23/24.
 */
@Slf4j
@Component
public class DefaultNamedQueriesDefinitionService extends AbstractProjectScopedService<NamedQueriesDefinition> implements NamedQueriesDefinitionService {

    private final NamedQueriesDefinitionRepository namedQueriesRepository;
    private final EventService eventService;

    public DefaultNamedQueriesDefinitionService(NamedQueriesDefinitionRepository repository,
                                                EventService eventService,
                                                SecurityContext securityContext) {
        super(repository, securityContext);
        this.namedQueriesRepository = repository;
        this.eventService = eventService;
    }


    @Override
    public Future<NamedQueriesDefinition> findByApplicationAndEntityDefinition(String applicationId, String entityDefinitionName) {
        return namedQueriesRepository.findByApplicationAndEntityDefinition(applicationId, entityDefinitionName, requireOrganizationId());
    }

    // The gateway checks a write on the entity definition its id names, so the id must be the one of the definition
    // the queries are written for
    @Override
    protected Future<Void> beforeSave(NamedQueriesDefinition definition) {
        Validate.notBlank(definition.getApplicationId(), "NamedQueriesDefinition applicationId cannot be blank");
        Validate.notBlank(definition.getEntityDefinitionName(), "NamedQueriesDefinition entityDefinitionName cannot be blank");
        String id = DomainUtil.createEntityDefinitionId(definition.applicationKey(), definition.getEntityDefinitionName());
        if (definition.getId() == null) {
            definition.setId(id);
        }
        Validate.isTrue(id.equals(definition.getId()),
                        "NamedQueriesDefinition id '%s' must be the id of its entity definition, '%s'", definition.getId(), id);
        return Future.succeededFuture();
    }

    // Every mutation below uses the Refresh.WaitFor write/delete variant and evicts after
    // it completes. The cache loader reads through a search, so the mutation must be
    // searchable before the eviction fires — otherwise a reload between the eviction and
    // the index refresh re-caches the old row with no eviction left to clear it. The
    // mutation completes once every node holding the caches has evicted.

    @Override
    public Future<NamedQueriesDefinition> save(NamedQueriesDefinition definition) {
        // TODO: preprocess queries to correct index name and add Metadata about query type to be used by other parts of the system
        //       The Query type information will speed up other areas the need this as well
        return saveSync(definition);
    }

    @Override
    public Future<NamedQueriesDefinition> saveSync(NamedQueriesDefinition definition) {
        return super.saveSync(definition).compose(this::evictModified);
    }

    @Override
    public Future<NamedQueriesDefinition> create(NamedQueriesDefinition definition) {
        return createSync(definition);
    }

    @Override
    public Future<NamedQueriesDefinition> createSync(NamedQueriesDefinition definition) {
        return super.createSync(definition).compose(this::evictModified);
    }

    /** Evicts cached queries after a successful write; shared by every save/create path. */
    private Future<NamedQueriesDefinition> evictModified(NamedQueriesDefinition definition) {
        return evict(definition).map(definition);
    }

    /**
     * Evicts the queries every node holding the caches compiled from the definition; the change to it is already
     * written when the eviction fails, and the failure says so.
     */
    private Future<Void> evict(NamedQueriesDefinition definition) {
        CacheEvictionEvent event = CacheEvictionEvent.namedQuery(definition.applicationKey(), definition.getId());
        return eventService.publish(event)
                           .recover(error -> Future.failedFuture(new CacheEvictionException(event, error)));
    }

    @Override
    public Future<Void> deleteById(String id) {
        return deleteAndEvict(id);
    }

    @Override
    public Future<Void> deleteByIdSync(String id) {
        return deleteAndEvict(id);
    }

    private Future<Void> deleteAndEvict(String id) {
        return findById(id)
                .compose(namedQuery -> {
                    if (namedQuery == null) {
                        return Future.failedFuture(
                                new IllegalArgumentException("NamedQuery cannot be found for id: " + id));
                    }
                    return super.deleteByIdSync(id)
                            .compose(v -> evict(namedQuery));
                });
    }

}
