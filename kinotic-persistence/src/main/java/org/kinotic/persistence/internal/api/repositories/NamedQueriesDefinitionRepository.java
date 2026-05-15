package org.kinotic.persistence.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractProjectRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.persistence.api.model.NamedQueriesDefinition;
import org.springframework.stereotype.Repository;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Repository
public class NamedQueriesDefinitionRepository extends AbstractProjectRepository<NamedQueriesDefinition> {

    public NamedQueriesDefinitionRepository(ElasticsearchAsyncClient esAsyncClient,
                                            CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_named_query_service_definition",
              NamedQueriesDefinition.class,
              esAsyncClient,
              crudServiceTemplate);
    }

    public CompletableFuture<NamedQueriesDefinition> findByApplicationAndEntityDefinition(String applicationId,
                                                                                          String entityDefinitionName) {
        return findByApplicationAndEntityDefinition(applicationId, entityDefinitionName, null);
    }

    /**
     * Finds the first definition for the given application + entityDefinitionName. The
     * repository sets a default query filtering by both terms; the consumer fires afterward
     * and may augment routing or install a composed query via
     * {@link #buildApplicationEntityQuery(String, String, Query)}.
     */
    public CompletableFuture<NamedQueriesDefinition> findByApplicationAndEntityDefinition(String applicationId,
                                                                                          String entityDefinitionName,
                                                                                          Consumer<SearchRequest.Builder> builderConsumer) {
        Query baseQuery = buildApplicationEntityQuery(applicationId, entityDefinitionName, null);
        return crudServiceTemplate.search(indexName, Pageable.ofSize(1), type, b -> {
            b.query(baseQuery);
            if (builderConsumer != null) builderConsumer.accept(b);
        }).thenApply(page -> page.getContent() != null && !page.getContent().isEmpty()
                ? page.getContent().getFirst()
                : null);
    }

    /**
     * Builds a bool query whose {@code filter} clauses include the {@code applicationId} and
     * {@code entityDefinitionName} terms and, when supplied, an additional caller-provided filter.
     */
    public Query buildApplicationEntityQuery(String applicationId, String entityDefinitionName, Query extraFilter) {
        return Query.of(q -> q.bool(b -> {
            b.filter(TermQuery.of(tq -> tq.field("applicationId").value(applicationId))._toQuery(),
                     TermQuery.of(tq -> tq.field("entityDefinitionName").value(entityDefinitionName))._toQuery());
            if (extraFilter != null) {
                b.filter(extraFilter);
            }
            return b;
        }));
    }
}
