package org.kinotic.persistence.internal.api.services;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.internal.api.services.AbstractProjectScopedService;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.internal.api.repositories.EntityDefinitionRepository;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Created by Navíd Mitchell 🤪on 6/25/23.
 */
@Component
public class DefaultEntityDefinitionDAO extends AbstractProjectScopedService<EntityDefinition> implements EntityDefinitionDAO {

    private final EntityDefinitionRepository entityDefinitionRepository;

    public DefaultEntityDefinitionDAO(EntityDefinitionRepository repository,
                                      SecurityContext securityContext) {
        super(repository, securityContext);
        this.entityDefinitionRepository = repository;
    }

    @Override
    public CompletableFuture<Page<EntityDefinition>> findAllPublishedForApplication(String applicationId, Pageable pageable) {
        return entityDefinitionRepository.findAllPublishedForApplication(applicationId, pageable, requireOrganizationId());
    }

}
