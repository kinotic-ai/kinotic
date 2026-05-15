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
 * Adds {@code applicationId}-scoped query helpers; org-scoping is applied automatically when
 * the caller passes a non-null {@code orgId}.
 */
public abstract class AbstractApplicationScopedRepository<T extends ApplicationScoped<String>>
        extends AbstractOrganizationScopedRepository<T> {

    public AbstractApplicationScopedRepository(String indexName,
                                               Class<T> type,
                                               ElasticsearchAsyncClient esAsyncClient,
                                               CrudServiceTemplate crudServiceTemplate) {
        super(indexName, type, esAsyncClient, crudServiceTemplate);
    }

    public CompletableFuture<Long> countForApplication(String applicationId) {
        return countForApplication(applicationId, null);
    }

    public CompletableFuture<Long> countForApplication(String applicationId, String orgId) {
        Query query = composeOrgFilter(orgId, applicationIdFilter(applicationId));
        return doCount(b -> {
            if (orgId != null) b.routing(orgId);
            b.query(query);
        });
    }

    public CompletableFuture<Page<T>> findAllForApplication(String applicationId, Pageable pageable) {
        return findAllForApplication(applicationId, pageable, null);
    }

    public CompletableFuture<Page<T>> findAllForApplication(String applicationId, Pageable pageable, String orgId) {
        Query query = composeOrgFilter(orgId, applicationIdFilter(applicationId));
        return doSearch(pageable, b -> {
            if (orgId != null) b.routing(orgId);
            b.query(query);
        });
    }

    private Query applicationIdFilter(String applicationId) {
        return TermQuery.of(t -> t.field("applicationId").value(applicationId))._toQuery();
    }
}
