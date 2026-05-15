package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.ProjectScoped;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.concurrent.CompletableFuture;

/**
 * Repository tier for entities that belong to a project within an application and organization.
 * Adds {@code projectId}-scoped query helpers; org-scoping is applied automatically when the
 * caller passes a non-null {@code orgId}.
 */
public abstract class AbstractProjectScopedRepository<T extends ProjectScoped<String>>
        extends AbstractApplicationScopedRepository<T> {

    public AbstractProjectScopedRepository(String indexName,
                                           Class<T> type,
                                           ElasticsearchAsyncClient esAsyncClient,
                                           CrudServiceTemplate crudServiceTemplate) {
        super(indexName, type, esAsyncClient, crudServiceTemplate);
    }

    public CompletableFuture<Long> countForProject(String projectId) {
        return countForProject(projectId, null);
    }

    public CompletableFuture<Long> countForProject(String projectId, String orgId) {
        Query query = composeOrgFilter(orgId, projectIdFilter(projectId));
        return count(b -> {
            if (orgId != null) b.routing(orgId);
            b.query(query);
        });
    }

    public CompletableFuture<Page<T>> findAllForProject(String projectId, Pageable pageable) {
        return findAllForProject(projectId, pageable, null);
    }

    public CompletableFuture<Page<T>> findAllForProject(String projectId, Pageable pageable, String orgId) {
        Query query = composeOrgFilter(orgId, projectIdFilter(projectId));
        return findAll(pageable, b -> {
            if (orgId != null) b.routing(orgId);
            b.query(query);
        });
    }

    private Query projectIdFilter(String projectId) {
        return TermQuery.of(t -> t.field("projectId").value(projectId))._toQuery();
    }
}
