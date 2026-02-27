package org.kinotic.persistence.api.services.security;

import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.NamedQueryOperation;
import org.kinotic.persistence.api.model.EntityOperation;

import java.util.concurrent.CompletableFuture;


/**
 * Factory for creating AuthorizationService instances.
 * Created by Navíd Mitchell 🤪on 1/30/25.
 */
public interface AuthorizationServiceFactory {

    /**
     * Creates an AuthorizationService for the given EntityDefinition
     * @param entityDefinition to create the AuthorizationService for
     * @return a CompletableFuture that will complete with the AuthorizationService
     */
    CompletableFuture<AuthorizationService<EntityOperation>> createEntityDefinitionAuthorizationService(EntityDefinition entityDefinition);

    /**
     * Creates an AuthorizationService for the given named query
     * @param namedQuery to create the AuthorizationService for
     * @return a CompletableFuture that will complete with the AuthorizationService
     */
    CompletableFuture<AuthorizationService<NamedQueryOperation>> createNamedQueryAuthorizationService(FunctionDefinition namedQuery);

}
