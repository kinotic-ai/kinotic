package org.kinotic.os.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.kinotic.core.api.crud.ApplicationScopedCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.os.api.model.ApplicationScoped;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractApplicationCrudService<T extends ApplicationScoped<String>>
        extends AbstractCrudService<T>
        implements ApplicationScopedCrudService<T, String> {

    public AbstractApplicationCrudService(String indexName,
                                          Class<T> type,
                                          ElasticsearchAsyncClient esAsyncClient,
                                          CrudServiceTemplate crudServiceTemplate,
                                          SecurityContext securityContext) {
        super(indexName, type, esAsyncClient, crudServiceTemplate, securityContext);
    }

    @Override
    public CompletableFuture<Long> countForApplication(String applicationId) {
        String orgId = getOrganizationIdIfEnforced();
        return crudServiceTemplate.count(indexName, builder -> {
            builder.query(q -> q.bool(b -> {
                b.filter(TermQuery.of(tq -> tq.field("applicationId").value(applicationId))._toQuery());
                if (orgId != null) {
                    b.filter(TermQuery.of(tq -> tq.field("organizationId").value(orgId))._toQuery());
                }
                return b;
            }));
        });
    }

    @Override
    public CompletableFuture<Page<T>> findAllForApplication(String applicationId, Pageable pageable) {
        String orgId = getOrganizationIdIfEnforced();
        return crudServiceTemplate.search(indexName, pageable, type, builder -> {
            builder.query(q -> q.bool(b -> {
                b.filter(TermQuery.of(tq -> tq.field("applicationId").value(applicationId))._toQuery());
                if (orgId != null) {
                    b.filter(TermQuery.of(tq -> tq.field("organizationId").value(orgId))._toQuery());
                }
                return b;
            }));
        });
    }

}
