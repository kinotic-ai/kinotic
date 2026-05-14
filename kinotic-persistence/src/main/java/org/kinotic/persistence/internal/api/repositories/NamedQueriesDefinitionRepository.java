package org.kinotic.persistence.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractProjectRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.persistence.api.model.NamedQueriesDefinition;
import org.springframework.stereotype.Repository;

import java.util.concurrent.CompletableFuture;

@Repository
public class NamedQueriesDefinitionRepository extends AbstractProjectRepository<NamedQueriesDefinition> {

    public NamedQueriesDefinitionRepository(ElasticsearchAsyncClient esAsyncClient,
                                            CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_named_query_service_definition",
              NamedQueriesDefinition.class,
              esAsyncClient,
              crudServiceTemplate);
    }

    /**
     * Finds the first definition for the given application + entityDefinitionName, optionally
     * routed and constrained by an extra filter (typically the org filter supplied by the service).
     */
    public CompletableFuture<NamedQueriesDefinition> findByApplicationAndEntityDefinition(String applicationId,
                                                                                          String entityDefinitionName,
                                                                                          String routing,
                                                                                          Query extraFilter) {
        Query query = Query.of(q -> q.bool(b -> {
            b.filter(TermQuery.of(tq -> tq.field("applicationId").value(applicationId))._toQuery(),
                     TermQuery.of(tq -> tq.field("entityDefinitionName").value(entityDefinitionName))._toQuery());
            if (extraFilter != null) {
                b.filter(extraFilter);
            }
            return b;
        }));
        return crudServiceTemplate.search(indexName, Pageable.ofSize(1), type, b -> {
            if (routing != null) b.routing(routing);
            b.query(query);
        }).thenApply(page -> page.getContent() != null && !page.getContent().isEmpty()
                ? page.getContent().getFirst()
                : null);
    }
}
