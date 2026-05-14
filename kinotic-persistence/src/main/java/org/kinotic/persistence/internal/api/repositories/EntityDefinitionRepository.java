package org.kinotic.persistence.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractProjectRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.springframework.stereotype.Repository;

import java.util.concurrent.CompletableFuture;

@Repository
public class EntityDefinitionRepository extends AbstractProjectRepository<EntityDefinition> {

    public EntityDefinitionRepository(ElasticsearchAsyncClient esAsyncClient,
                                      CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_entity_definition", EntityDefinition.class, esAsyncClient, crudServiceTemplate);
    }

    /**
     * Returns a page of published entity definitions for the given application, optionally
     * routed and constrained by an extra filter (typically the org filter supplied by the service).
     */
    public CompletableFuture<Page<EntityDefinition>> findAllPublishedForApplication(String applicationId,
                                                                                    Pageable pageable,
                                                                                    String routing,
                                                                                    Query extraFilter) {
        Query query = Query.of(q -> q.bool(b -> {
            b.filter(TermQuery.of(tq -> tq.field("applicationId").value(applicationId))._toQuery(),
                     TermQuery.of(tq -> tq.field("published").value(true))._toQuery());
            if (extraFilter != null) {
                b.filter(extraFilter);
            }
            return b;
        }));
        return crudServiceTemplate.search(indexName, pageable, type, b -> {
            if (routing != null) b.routing(routing);
            b.query(query);
        });
    }
}
