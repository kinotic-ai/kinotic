package org.kinotic.persistence.internal.api.repositories;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.internal.api.repositories.AbstractProjectScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.persistence.api.model.NamedQueriesDefinition;
import org.springframework.stereotype.Component;

@Component
public class NamedQueriesDefinitionRepository extends AbstractProjectScopedRepository<NamedQueriesDefinition> {

    public NamedQueriesDefinitionRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_named_query_service_definition",
              NamedQueriesDefinition.class,
              crudServiceTemplate);
    }

    public Future<NamedQueriesDefinition> findByApplicationAndEntityDefinition(String applicationId,
                                                                               String entityDefinitionName,
                                                                               String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return findFirst(b -> b.routing(orgId).query(composeOrgFilter(orgId,
                                                                      applicationIdFilter(applicationId),
                                                                      termFilter("entityDefinitionName", entityDefinitionName))));
    }
}
