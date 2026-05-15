package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.ApplicationScoped;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Repository tier for entities that belong to an application within an organization.
 * Adds {@code applicationId}-scoped query helpers without applying any org filtering &mdash;
 * the service tier composes one of these and layers org enforcement on top via the
 * {@code Consumer<...Builder>} overloads.
 */
public abstract class AbstractApplicationRepository<T extends ApplicationScoped<String>>
        extends AbstractRepository<T> {

    public AbstractApplicationRepository(String indexName,
                                         Class<T> type,
                                         ElasticsearchAsyncClient esAsyncClient,
                                         CrudServiceTemplate crudServiceTemplate) {
        super(indexName, type, esAsyncClient, crudServiceTemplate);
    }

    public CompletableFuture<Long> countForApplication(String applicationId) {
        return countForApplication(applicationId, null);
    }

    /**
     * Counts entities for the application. The repository sets a default query filtering by
     * {@code applicationId}; the consumer fires afterward and may augment routing or call
     * {@link #buildApplicationQuery(String, Query)} to install a composed query.
     */
    public CompletableFuture<Long> countForApplication(String applicationId, Consumer<CountRequest.Builder> builderConsumer) {
        Query baseQuery = buildApplicationQuery(applicationId, null);
        return crudServiceTemplate.count(indexName, b -> {
            b.query(baseQuery);
            if (builderConsumer != null) builderConsumer.accept(b);
        });
    }

    public CompletableFuture<Page<T>> findAllForApplication(String applicationId, Pageable pageable) {
        return findAllForApplication(applicationId, pageable, null);
    }

    /**
     * Returns a page of entities for the application. The repository sets a default query
     * filtering by {@code applicationId}; the consumer fires afterward and may augment
     * routing or install a composed query via {@link #buildApplicationQuery(String, Query)}.
     */
    public CompletableFuture<Page<T>> findAllForApplication(String applicationId,
                                                            Pageable pageable,
                                                            Consumer<SearchRequest.Builder> builderConsumer) {
        Query baseQuery = buildApplicationQuery(applicationId, null);
        return crudServiceTemplate.search(indexName, pageable, type, b -> {
            b.query(baseQuery);
            if (builderConsumer != null) builderConsumer.accept(b);
        });
    }

    /**
     * Builds a bool query whose {@code filter} clauses include the {@code applicationId} term
     * and, when supplied, an additional caller-provided filter. Exposed so the service tier
     * can compose this with org filters and install the result through the consumer overloads.
     */
    public Query buildApplicationQuery(String applicationId, Query extraFilter) {
        return Query.of(q -> q.bool(b -> {
            b.filter(TermQuery.of(tq -> tq.field("applicationId").value(applicationId))._toQuery());
            if (extraFilter != null) {
                b.filter(extraFilter);
            }
            return b;
        }));
    }
}
