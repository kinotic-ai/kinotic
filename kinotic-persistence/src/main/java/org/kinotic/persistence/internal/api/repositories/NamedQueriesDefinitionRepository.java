package org.kinotic.persistence.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractProjectRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.persistence.api.model.NamedQueriesDefinition;
import org.springframework.stereotype.Repository;

import java.util.concurrent.CompletableFuture;

@Repository
public class NamedQueriesDefinitionRepository extends AbstractProjectRepository<NamedQueriesDefinition> {

    public NamedQueriesDefinitionRepository(ElasticsearchAsyncClient esAsyncClient,
                                            CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_named_query_service_definition",
              NamedQueriesDefinition.class,
              esAsyncClient,
              crudServiceTemplate);
    }

    public CompletableFuture<NamedQueriesDefinition> findByApplicationAndEntityDefinition(String applicationId,
                                                                                          String entityDefinitionName) {
        return findByApplicationAndEntityDefinition(applicationId, entityDefinitionName, null);
    }

    public CompletableFuture<NamedQueriesDefinition> findByApplicationAndEntityDefinition(String applicationId,
                                                                                          String entityDefinitionName,
                                                                                          String orgId) {
        Query query = composeOrgFilter(orgId,
                                       TermQuery.of(t -> t.field("applicationId").value(applicationId))._toQuery(),
                                       TermQuery.of(t -> t.field("entityDefinitionName").value(entityDefinitionName))._toQuery());
        return doSearch(Pageable.ofSize(1), b -> {
            if (orgId != null) b.routing(orgId);
            b.query(query);
        }).thenApply(page -> page.getContent() != null && !page.getContent().isEmpty()
                ? page.getContent().getFirst()
                : null);
    }
}
