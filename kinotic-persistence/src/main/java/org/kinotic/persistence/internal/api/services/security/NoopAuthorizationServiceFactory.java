package org.kinotic.persistence.internal.api.services.security;

import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.persistence.api.domain.EntityOperation;
import org.kinotic.persistence.api.domain.NamedQueryOperation;
import org.kinotic.persistence.api.domain.Structure;
import org.kinotic.persistence.api.services.security.AuthorizationService;
import org.kinotic.persistence.api.services.security.AuthorizationServiceFactory;

import java.util.concurrent.CompletableFuture;

public class NoopAuthorizationServiceFactory implements AuthorizationServiceFactory {

    @Override
    public CompletableFuture<AuthorizationService<EntityOperation>> createStructureAuthorizationService(Structure structure) {
        return CompletableFuture.completedFuture(new NoopAuthorizationService<>());
    }

    @Override
    public CompletableFuture<AuthorizationService<NamedQueryOperation>> createNamedQueryAuthorizationService(FunctionDefinition namedQuery) {
        return CompletableFuture.completedFuture(new NoopAuthorizationService<>());
    }
}
