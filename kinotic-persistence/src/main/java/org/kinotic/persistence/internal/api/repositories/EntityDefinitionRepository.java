package org.kinotic.persistence.internal.api.repositories;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractProjectScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class EntityDefinitionRepository extends AbstractProjectScopedRepository<EntityDefinition> {

    public EntityDefinitionRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_entity_definition", EntityDefinition.class, crudServiceTemplate);
    }

    public CompletableFuture<Page<EntityDefinition>> findAllPublishedForApplication(String applicationId,
                                                                                    String orgId,
                                                                                    Pageable pageable) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return findAll(pageable,
                       b -> b.routing(orgId).query(composeOrgFilter(orgId,
                                                                    applicationIdFilter(applicationId),
                                                                    termFilter("published", true))));
    }
}
