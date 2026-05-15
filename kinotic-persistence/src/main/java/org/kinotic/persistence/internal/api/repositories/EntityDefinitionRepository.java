package org.kinotic.persistence.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractProjectScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Repository
public class EntityDefinitionRepository extends AbstractProjectScopedRepository<EntityDefinition> {

    public EntityDefinitionRepository(ElasticsearchAsyncClient esAsyncClient,
                                      CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_entity_definition", EntityDefinition.class, esAsyncClient, crudServiceTemplate);
    }

    public CompletableFuture<Page<EntityDefinition>> findAllPublishedForApplication(String applicationId, Pageable pageable) {
        return findAll(pageable,
                       b -> b.query(composeOrgFilter(null,
                                                     applicationIdFilter(applicationId),
                                                     publishedFilter())));
    }

    public CompletableFuture<Page<EntityDefinition>> findAllPublishedForApplication(String applicationId,
                                                                                    Pageable pageable,
                                                                                    String orgId) {
        Objects.requireNonNull(orgId, "orgId");
        return findAll(pageable,
                       b -> b.routing(orgId).query(composeOrgFilter(orgId,
                                                                    applicationIdFilter(applicationId),
                                                                    publishedFilter())));
    }

    private Query applicationIdFilter(String applicationId) {
        return TermQuery.of(t -> t.field("applicationId").value(applicationId))._toQuery();
    }

    private Query publishedFilter() {
        return TermQuery.of(t -> t.field("published").value(true))._toQuery();
    }
}
