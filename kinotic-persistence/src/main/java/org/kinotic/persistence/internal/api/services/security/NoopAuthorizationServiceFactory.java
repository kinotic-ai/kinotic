package org.kinotic.persistence.internal.api.services.security;

import io.vertx.core.Future;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.EntityOperation;
import org.kinotic.persistence.api.model.NamedQueryOperation;
import org.kinotic.persistence.api.services.security.AuthorizationService;
import org.kinotic.persistence.api.services.security.AuthorizationServiceFactory;

public class NoopAuthorizationServiceFactory implements AuthorizationServiceFactory {

    @Override
    public Future<AuthorizationService<EntityOperation>> createEntityDefinitionAuthorizationService(EntityDefinition entityDefinition) {
        return Future.succeededFuture(new NoopAuthorizationService<>());
    }

    @Override
    public Future<AuthorizationService<NamedQueryOperation>> createNamedQueryAuthorizationService(FunctionDefinition namedQuery) {
        return Future.succeededFuture(new NoopAuthorizationService<>());
    }
}
