package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.ProjectScoped;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Repository tier for entities that belong to a project within an application and organization.
 * Adds {@code projectId}-scoped query helpers and routes {@code findById}/{@code deleteById}
 * to the shard determined by the project-id prefix encoded in the composite document id.
 */
public abstract class AbstractProjectRepository<T extends ProjectScoped<String>>
        extends AbstractApplicationRepository<T> {

    public AbstractProjectRepository(String indexName,
                                     Class<T> type,
                                     ElasticsearchAsyncClient esAsyncClient,
                                     CrudServiceTemplate crudServiceTemplate) {
        super(indexName, type, esAsyncClient, crudServiceTemplate);
    }

    @Override
    protected String getRoutingKeyFromId(String id) {
        if (id != null) {
            int dotIndex = id.indexOf('.');
            if (dotIndex > 0) {
                return id.substring(0, dotIndex);
            }
        }
        return null;
    }

    public CompletableFuture<Long> countForProject(String projectId) {
        return countForProject(projectId, null);
    }

    /**
     * Counts entities for the project. The repository sets a default query filtering by
     * {@code projectId}; the consumer fires afterward and may augment routing or install a
     * composed query via {@link #buildProjectQuery(String, Query)}.
     */
    public CompletableFuture<Long> countForProject(String projectId, Consumer<CountRequest.Builder> builderConsumer) {
        Query baseQuery = buildProjectQuery(projectId, null);
        return crudServiceTemplate.count(indexName, b -> {
            b.query(baseQuery);
            if (builderConsumer != null) builderConsumer.accept(b);
        });
    }

    public CompletableFuture<Page<T>> findAllForProject(String projectId, Pageable pageable) {
        return findAllForProject(projectId, pageable, null);
    }

    /**
     * Returns a page of entities for the project. The repository sets a default query
     * filtering by {@code projectId}; the consumer fires afterward and may augment routing
     * or install a composed query via {@link #buildProjectQuery(String, Query)}.
     */
    public CompletableFuture<Page<T>> findAllForProject(String projectId,
                                                        Pageable pageable,
                                                        Consumer<SearchRequest.Builder> builderConsumer) {
        Query baseQuery = buildProjectQuery(projectId, null);
        return crudServiceTemplate.search(indexName, pageable, type, b -> {
            b.query(baseQuery);
            if (builderConsumer != null) builderConsumer.accept(b);
        });
    }

    /**
     * Builds a bool query whose {@code filter} clauses include the {@code projectId} term
     * and, when supplied, an additional caller-provided filter. Exposed so the service tier
     * can compose this with org filters and install the result through the consumer overloads.
     */
    public Query buildProjectQuery(String projectId, Query extraFilter) {
        return Query.of(q -> q.bool(b -> {
            b.filter(TermQuery.of(tq -> tq.field("projectId").value(projectId))._toQuery());
            if (extraFilter != null) {
                b.filter(extraFilter);
            }
            return b;
        }));
    }
}
