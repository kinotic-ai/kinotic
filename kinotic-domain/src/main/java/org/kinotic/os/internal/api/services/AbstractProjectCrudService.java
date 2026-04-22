package org.kinotic.os.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
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
    public CompletableFuture<Long> countForProject(String projectId) {
        String orgId = getOrganizationIdIfEnforced();
        return crudServiceTemplate.count(indexName, builder -> {
            if (orgId != null) {
                builder.routing(orgId);
            }
            builder.query(q -> q.bool(b -> {
                b.filter(TermQuery.of(tq -> tq.field("projectId").value(projectId))._toQuery());
                if (orgId != null) {
                    b.filter(TermQuery.of(tq -> tq.field("organizationId").value(orgId))._toQuery());
                }
                return b;
            }));
        });
    }

    @Override
    public CompletableFuture<Page<T>> findAllForProject(String projectId, Pageable pageable) {
        String orgId = getOrganizationIdIfEnforced();
        return crudServiceTemplate.search(indexName, pageable, type, builder -> {
            if (orgId != null) {
                builder.routing(orgId);
            }
            builder.query(q -> q.bool(b -> {
                b.filter(TermQuery.of(tq -> tq.field("projectId").value(projectId))._toQuery());
                if (orgId != null) {
                    b.filter(TermQuery.of(tq -> tq.field("organizationId").value(orgId))._toQuery());
                }
                return b;
            }));
        });
    }

}
