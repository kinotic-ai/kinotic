package org.kinotic.persistence.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractProjectScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.persistence.api.model.NamedQueriesDefinition;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class NamedQueriesDefinitionRepository extends AbstractProjectScopedRepository<NamedQueriesDefinition> {

    public NamedQueriesDefinitionRepository(ElasticsearchAsyncClient esAsyncClient,
                                            CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_named_query_service_definition",
              NamedQueriesDefinition.class,
              esAsyncClient,
              crudServiceTemplate);
    }

    public CompletableFuture<NamedQueriesDefinition> findByApplicationAndEntityDefinition(String applicationId,
                                                                                          String entityDefinitionName,
                                                                                          String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return findAll(Pageable.ofSize(1),
                       b -> b.routing(orgId).query(composeOrgFilter(orgId,
                                                                    applicationIdFilter(applicationId),
                                                                    entityDefinitionNameFilter(entityDefinitionName))))
                .thenApply(page -> page.getContent() != null && !page.getContent().isEmpty()
                        ? page.getContent().getFirst()
                        : null);
    }

    private Query applicationIdFilter(String applicationId) {
        return TermQuery.of(t -> t.field("applicationId").value(applicationId))._toQuery();
    }

    private Query entityDefinitionNameFilter(String entityDefinitionName) {
        return TermQuery.of(t -> t.field("entityDefinitionName").value(entityDefinitionName))._toQuery();
    }
}
