package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.ProjectScoped;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Repository tier for entities that belong to a project within an application and organization.
 * Adds {@code projectId}-scoped query helpers, each with an {@code orgId}-aware overload.
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
        return count(b -> b.query(composeOrgFilter(null, projectIdFilter(projectId))));
    }

    public CompletableFuture<Long> countForProject(String projectId, String orgId) {
        Objects.requireNonNull(orgId, "orgId");
        return count(b -> b.routing(orgId).query(composeOrgFilter(orgId, projectIdFilter(projectId))));
    }

    public CompletableFuture<Page<T>> findAllForProject(String projectId, Pageable pageable) {
        return findAll(pageable, b -> b.query(composeOrgFilter(null, projectIdFilter(projectId))));
    }

    public CompletableFuture<Page<T>> findAllForProject(String projectId, Pageable pageable, String orgId) {
        Objects.requireNonNull(orgId, "orgId");
        return findAll(pageable, b -> b.routing(orgId).query(composeOrgFilter(orgId, projectIdFilter(projectId))));
    }

    private Query projectIdFilter(String projectId) {
        return TermQuery.of(t -> t.field("projectId").value(projectId))._toQuery();
    }
}
