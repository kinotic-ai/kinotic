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

import java.util.concurrent.CompletableFuture;

@Repository
public class EntityDefinitionRepository extends AbstractProjectScopedRepository<EntityDefinition> {

    public EntityDefinitionRepository(ElasticsearchAsyncClient esAsyncClient,
                                      CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_entity_definition", EntityDefinition.class, esAsyncClient, crudServiceTemplate);
    }

    public CompletableFuture<Page<EntityDefinition>> findAllPublishedForApplication(String applicationId, Pageable pageable) {
        return findAllPublishedForApplication(applicationId, pageable, null);
    }

    public CompletableFuture<Page<EntityDefinition>> findAllPublishedForApplication(String applicationId,
                                                                                    Pageable pageable,
                                                                                    String orgId) {
        Query query = composeOrgFilter(orgId,
                                       TermQuery.of(t -> t.field("applicationId").value(applicationId))._toQuery(),
                                       TermQuery.of(t -> t.field("published").value(true))._toQuery());
        return doSearch(pageable, b -> {
            if (orgId != null) b.routing(orgId);
            b.query(query);
        });
    }
}
