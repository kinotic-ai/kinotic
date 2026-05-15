package org.kinotic.persistence.internal.api.services;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.internal.api.services.AbstractProjectCrudService;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.internal.api.repositories.EntityDefinitionRepository;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪on 6/25/23.
 */
@Component
public class DefaultEntityDefinitionDAO extends AbstractProjectCrudService<EntityDefinition> implements EntityDefinitionDAO {

    private final EntityDefinitionRepository entityDefinitionRepository;

    public DefaultEntityDefinitionDAO(EntityDefinitionRepository repository,
                                      SecurityContext securityContext) {
        super(repository, securityContext);
        this.entityDefinitionRepository = repository;
    }

    @Override
    public CompletableFuture<Page<EntityDefinition>> findAllPublishedForApplication(String applicationId, Pageable pageable) {
        String orgId = getOrganizationIdIfEnforced();
        if (orgId == null) {
            return entityDefinitionRepository.findAllPublishedForApplication(applicationId, pageable);
        }
        Query composed = entityDefinitionRepository.buildPublishedApplicationQuery(applicationId, buildOrgFilterQuery(orgId));
        return entityDefinitionRepository.findAllPublishedForApplication(applicationId, pageable,
                                                                         b -> b.routing(orgId).query(composed));
    }

}
