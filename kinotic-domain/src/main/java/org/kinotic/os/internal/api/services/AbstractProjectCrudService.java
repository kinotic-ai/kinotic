package org.kinotic.os.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.ProjectScopedCrudService;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.os.api.model.ProjectScoped;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractProjectCrudService<T extends ProjectScoped<String>>
        extends AbstractApplicationCrudService<T>
        implements ProjectScopedCrudService<T, String> {

    public AbstractProjectCrudService(String indexName,
                                      Class<T> type,
                                      ElasticsearchAsyncClient esAsyncClient,
                                      CrudServiceTemplate crudServiceTemplate,
                                      SecurityContext securityContext) {
        super(indexName, type, esAsyncClient, crudServiceTemplate, securityContext);
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
        String orgId = getOrganizationIdIfEnforced();
        Query query = buildProjectQuery(projectId, orgId);
        return crudServiceTemplate.count(indexName, b -> {
            if (orgId != null) b.routing(orgId);
            b.query(query);
        });
    }

    @Override
    public CompletableFuture<Page<T>> findAllForProject(String projectId, Pageable pageable) {
        String orgId = getOrganizationIdIfEnforced();
        Query query = buildProjectQuery(projectId, orgId);
        return crudServiceTemplate.search(indexName, pageable, type, b -> {
            if (orgId != null) b.routing(orgId);
            b.query(query);
        });
    }

    private Query buildProjectQuery(String projectId, String orgId) {
        return Query.of(q -> q.bool(b -> {
            b.filter(TermQuery.of(tq -> tq.field("projectId").value(projectId))._toQuery());
            if (orgId != null) {
                b.filter(TermQuery.of(tq -> tq.field("organizationId").value(orgId))._toQuery());
            }
            return b;
        }));
    }

}
