package org.kinotic.persistence.internal.api.services.security;

import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.EntityOperation;
import org.kinotic.persistence.api.model.NamedQueryOperation;
import org.kinotic.persistence.api.services.security.AuthorizationService;
import org.kinotic.persistence.api.services.security.AuthorizationServiceFactory;

import java.util.concurrent.CompletableFuture;

public class NoopAuthorizationServiceFactory implements AuthorizationServiceFactory {

    @Override
    public CompletableFuture<AuthorizationService<EntityOperation>> createEntityDefinitionAuthorizationService(EntityDefinition entityDefinition) {
        return CompletableFuture.completedFuture(new NoopAuthorizationService<>());
    }

    @Override
    public CompletableFuture<AuthorizationService<NamedQueryOperation>> createNamedQueryAuthorizationService(FunctionDefinition namedQuery) {
        return CompletableFuture.completedFuture(new NoopAuthorizationService<>());
    }
}
