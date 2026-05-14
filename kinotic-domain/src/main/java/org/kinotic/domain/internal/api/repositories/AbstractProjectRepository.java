package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.ProjectScopedCrudService;
import org.kinotic.domain.api.model.ProjectScoped;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;

import java.util.concurrent.CompletableFuture;

/**
 * Repository tier for entities that belong to a project within an application and organization.
 * Adds {@code projectId}-scoped query helpers and routes {@code findById}/{@code deleteById}
 * to the shard determined by the project-id prefix encoded in the composite document id.
 */
public abstract class AbstractProjectRepository<T extends ProjectScoped<String>>
        extends AbstractApplicationRepository<T>
        implements ProjectScopedCrudService<T, String> {

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

    @Override
    public CompletableFuture<Long> countForProject(String projectId) {
        return countForProject(projectId, null, null);
    }

    /**
     * Counts entities for the project with optional routing and an optional extra filter.
     */
    public CompletableFuture<Long> countForProject(String projectId, String routing, Query extraFilter) {
        Query query = buildProjectQuery(projectId, extraFilter);
        return crudServiceTemplate.count(indexName, b -> {
            if (routing != null) b.routing(routing);
            b.query(query);
        });
    }

    @Override
    public CompletableFuture<Page<T>> findAllForProject(String projectId, Pageable pageable) {
        return findAllForProject(projectId, pageable, null, null);
    }

    /**
     * Returns a page of entities for the project with optional routing and an optional extra filter.
     */
    public CompletableFuture<Page<T>> findAllForProject(String projectId,
                                                        Pageable pageable,
                                                        String routing,
                                                        Query extraFilter) {
        Query query = buildProjectQuery(projectId, extraFilter);
        return crudServiceTemplate.search(indexName, pageable, type, b -> {
            if (routing != null) b.routing(routing);
            b.query(query);
        });
    }

    private Query buildProjectQuery(String projectId, Query extraFilter) {
        return Query.of(q -> q.bool(b -> {
            b.filter(TermQuery.of(tq -> tq.field("projectId").value(projectId))._toQuery());
            if (extraFilter != null) {
                b.filter(extraFilter);
            }
            return b;
        }));
    }
}
