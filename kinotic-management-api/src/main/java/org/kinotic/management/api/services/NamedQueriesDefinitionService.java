package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.domain.api.services.ProjectScopedCrudService;
import org.kinotic.domain.api.model.persistence.EntityDefinition;
import org.kinotic.domain.api.model.persistence.NamedQueriesDefinition;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzConstants;

/**
 * Created by Navíd Mitchell 🤪on 4/23/24.
 */
@Publish
@AuthzResource(value = AuthzConstants.ENTITY_DEFINITION_TYPE, parent = AuthzConstants.APPLICATION_TYPE)
public interface NamedQueriesDefinitionService extends ProjectScopedCrudService<NamedQueriesDefinition, String> {

    /**
     * Finds all {@link NamedQueriesDefinition} for a given application and {@link EntityDefinition}.
     * @param applicationId the id of the application that the {@link EntityDefinition} belongs to
     * @param entityDefinitionName the name of the {@link EntityDefinition} that this {@link NamedQueriesDefinition} is defined for
     * @return {@link Future} with the {@link NamedQueriesDefinition} or null if not found
     */
    Future<NamedQueriesDefinition> findByApplicationAndEntityDefinition(String applicationId, String entityDefinitionName);

}
