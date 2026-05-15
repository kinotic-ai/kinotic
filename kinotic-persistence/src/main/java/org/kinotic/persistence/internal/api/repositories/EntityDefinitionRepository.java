package org.kinotic.persistence.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractProjectRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.springframework.stereotype.Repository;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Repository
public class EntityDefinitionRepository extends AbstractProjectRepository<EntityDefinition> {

    public EntityDefinitionRepository(ElasticsearchAsyncClient esAsyncClient,
                                      CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_entity_definition", EntityDefinition.class, esAsyncClient, crudServiceTemplate);
    }

    public CompletableFuture<Page<EntityDefinition>> findAllPublishedForApplication(String applicationId, Pageable pageable) {
        return findAllPublishedForApplication(applicationId, pageable, null);
    }

    /**
     * Returns a page of published entity definitions for the given application. The
     * repository sets a default query filtering by {@code applicationId} and
     * {@code published=true}; the consumer fires afterward and may augment routing or
     * install a composed query via {@link #buildPublishedApplicationQuery(String, Query)}.
     */
    public CompletableFuture<Page<EntityDefinition>> findAllPublishedForApplication(String applicationId,
                                                                                    Pageable pageable,
                                                                                    Consumer<SearchRequest.Builder> builderConsumer) {
        Query baseQuery = buildPublishedApplicationQuery(applicationId, null);
        return crudServiceTemplate.search(indexName, pageable, type, b -> {
            b.query(baseQuery);
            if (builderConsumer != null) builderConsumer.accept(b);
        });
    }

    /**
     * Builds a bool query whose {@code filter} clauses include the {@code applicationId} term
     * and {@code published=true} and, when supplied, an additional caller-provided filter.
     */
    public Query buildPublishedApplicationQuery(String applicationId, Query extraFilter) {
        return Query.of(q -> q.bool(b -> {
            b.filter(TermQuery.of(tq -> tq.field("applicationId").value(applicationId))._toQuery(),
                     TermQuery.of(tq -> tq.field("published").value(true))._toQuery());
            if (extraFilter != null) {
                b.filter(extraFilter);
            }
            return b;
        }));
    }
}
