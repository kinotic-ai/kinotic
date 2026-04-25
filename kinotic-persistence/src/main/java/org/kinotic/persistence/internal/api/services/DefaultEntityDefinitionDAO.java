package org.kinotic.persistence.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.os.internal.api.services.AbstractProjectCrudService;
import org.kinotic.os.internal.api.services.CrudServiceTemplate;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪on 6/25/23.
 */
@Component
public class DefaultEntityDefinitionDAO extends AbstractProjectCrudService<EntityDefinition> implements EntityDefinitionDAO {

    public DefaultEntityDefinitionDAO(ElasticsearchAsyncClient esAsyncClient,
                                      CrudServiceTemplate crudServiceTemplate,
                                      SecurityContext securityContext) {
        super("kinotic_entity_definition",
              EntityDefinition.class,
              esAsyncClient,
              crudServiceTemplate,
              securityContext);
    }

    @Override
    public CompletableFuture<Page<EntityDefinition>> findAllPublishedForApplication(String applicationId, Pageable pageable) {
        String orgId = getOrganizationIdIfEnforced();
        Query query = buildPublishedApplicationQuery(applicationId, orgId);
        return crudServiceTemplate.search(indexName, pageable, type, b -> {
            if (orgId != null) b.routing(orgId);
            b.query(query);
        });
    }

    private Query buildPublishedApplicationQuery(String applicationId, String orgId) {
        return Query.of(q -> q.bool(b -> {
            b.filter(TermQuery.of(tq -> tq.field("applicationId").value(applicationId))._toQuery(),
                     TermQuery.of(tq -> tq.field("published").value(true))._toQuery());
            if (orgId != null) {
                b.filter(TermQuery.of(tq -> tq.field("organizationId").value(orgId))._toQuery());
            }
            return b;
        }));
    }

}
