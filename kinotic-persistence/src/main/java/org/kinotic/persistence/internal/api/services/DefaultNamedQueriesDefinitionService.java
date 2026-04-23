package org.kinotic.persistence.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.os.internal.api.services.AbstractProjectCrudService;
import org.kinotic.os.internal.api.services.CrudServiceTemplate;
import org.kinotic.persistence.api.model.NamedQueriesDefinition;
import org.kinotic.persistence.api.services.NamedQueriesDefinitionService;
import org.kinotic.persistence.internal.cache.events.CacheEvictionEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪 on 4/23/24.
 */
@Slf4j
@Component
public class DefaultNamedQueriesDefinitionService extends AbstractProjectCrudService<NamedQueriesDefinition> implements NamedQueriesDefinitionService {

    private final ApplicationEventPublisher eventPublisher;

    public DefaultNamedQueriesDefinitionService(CrudServiceTemplate crudServiceTemplate,
                                                ElasticsearchAsyncClient esAsyncClient,
                                                ApplicationEventPublisher eventPublisher,
                                                SecurityContext securityContext) {
        super("kinotic_named_query_service_definition",
              NamedQueriesDefinition.class,
              esAsyncClient,
              crudServiceTemplate,
              securityContext);

        this.eventPublisher = eventPublisher;
    }


    @Override
    public CompletableFuture<NamedQueriesDefinition> findByApplicationAndEntityDefinition(String applicationId, String entityDefinitionName) {
        String orgId = getOrganizationIdIfEnforced();
        Query query = buildApplicationEntityQuery(applicationId, entityDefinitionName, orgId);
        return crudServiceTemplate.search(indexName, Pageable.ofSize(1), type, b -> {
            if (orgId != null) b.routing(orgId);
            b.query(query);
        }).thenApply(page -> page.getContent() != null && !page.getContent().isEmpty()
                ? page.getContent().getFirst()
                : null);
    }

    private Query buildApplicationEntityQuery(String applicationId, String entityDefinitionName, String orgId) {
        return Query.of(q -> q.bool(b -> {
            b.filter(TermQuery.of(tq -> tq.field("applicationId").value(applicationId))._toQuery(),
                     TermQuery.of(tq -> tq.field("entityDefinitionName").value(entityDefinitionName))._toQuery());
            if (orgId != null) {
                b.filter(TermQuery.of(tq -> tq.field("organizationId").value(orgId))._toQuery());
            }
            return b;
        }));
    }

    @Override
    public CompletableFuture<NamedQueriesDefinition> save(NamedQueriesDefinition definition) {
        // TODO: preprocess queries to correct index name and add Metadata about query type to be used by other parts of the system
        //       The Query type information will speed up other areas the need this as well
        return super.save(definition)
                    .thenApply(namedQueriesDefinition -> {
                        this.eventPublisher.publishEvent(CacheEvictionEvent.localModifiedNamedQuery(definition.getOrganizationId(), definition.getApplicationId(), definition.getEntityDefinitionName(), definition.getId()));
                        return namedQueriesDefinition;
                    });
    }

    @Override
    public CompletableFuture<Void> deleteById(String id) {
        return findById(id)
                .thenCompose(namedQuery -> {
                    if (namedQuery == null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("NamedQuery cannot be found for id: " + id));
                    }
                    
                    return super.deleteById(id)
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
