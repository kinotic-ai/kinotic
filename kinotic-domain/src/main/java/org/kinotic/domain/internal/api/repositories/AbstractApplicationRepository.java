package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.ApplicationScoped;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.concurrent.CompletableFuture;

/**
 * Repository tier for entities that belong to an application within an organization.
 * Adds {@code applicationId}-scoped query helpers without applying any org filtering &mdash;
 * the service tier composes one of these and layers org enforcement on top.
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
        return countForApplication(applicationId, null, null);
    }

    /**
     * Counts entities for the application with optional routing and an optional extra filter
     * (typically the org filter supplied by the service tier).
     */
    public CompletableFuture<Long> countForApplication(String applicationId, String routing, Query extraFilter) {
        Query query = buildApplicationQuery(applicationId, extraFilter);
        return crudServiceTemplate.count(indexName, b -> {
            if (routing != null) b.routing(routing);
            b.query(query);
        });
    }

    public CompletableFuture<Page<T>> findAllForApplication(String applicationId, Pageable pageable) {
        return findAllForApplication(applicationId, pageable, null, null);
    }

    /**
     * Returns a page of entities for the application with optional routing and an optional
     * extra filter (typically the org filter supplied by the service tier).
     */
    public CompletableFuture<Page<T>> findAllForApplication(String applicationId,
                                                            Pageable pageable,
                                                            String routing,
                                                            Query extraFilter) {
        Query query = buildApplicationQuery(applicationId, extraFilter);
        return crudServiceTemplate.search(indexName, pageable, type, b -> {
            if (routing != null) b.routing(routing);
            b.query(query);
        });
    }

    private Query buildApplicationQuery(String applicationId, Query extraFilter) {
        return Query.of(q -> q.bool(b -> {
            b.filter(TermQuery.of(tq -> tq.field("applicationId").value(applicationId))._toQuery());
            if (extraFilter != null) {
                b.filter(extraFilter);
            }
            return b;
        }));
    }
}
