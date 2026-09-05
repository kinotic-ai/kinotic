package org.kinotic.persistence.api.services.security.graphos;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.EntityOperation;
import org.kinotic.persistence.api.model.NamedQueryOperation;
import org.kinotic.persistence.api.model.idl.decorators.PolicyDecorator;
import org.kinotic.persistence.api.services.security.AuthorizationService;
import org.kinotic.persistence.api.services.security.AuthorizationServiceFactory;
import org.kinotic.persistence.internal.api.services.security.NoopAuthorizationService;

/**
 * Created By Navíd Mitchell 🤪on 12/31/24
 */
@RequiredArgsConstructor
public class PolicyAuthorizationServiceFactory implements AuthorizationServiceFactory {

    private final PolicyAuthorizer policyAuthorizer;
    private final NoopAuthorizationService<NamedQueryOperation> noopAuthorizationService = new NoopAuthorizationService<>();

    @Override
    public Future<AuthorizationService<EntityOperation>> createEntityDefinitionAuthorizationService(EntityDefinition entityDefinition) {
        return Future.succeededFuture(new EntityDefinitionPolicyAuthorizationService(entityDefinition, policyAuthorizer));
    }

    @Override
    public Future<AuthorizationService<NamedQueryOperation>> createNamedQueryAuthorizationService(FunctionDefinition namedQuery) {
        Future<AuthorizationService<NamedQueryOperation>> ret;
        if(namedQuery.containsDecorator(PolicyDecorator.class)) {
            ret = Future.succeededFuture(new NamedQueryPolicyAuthorizationService(namedQuery, policyAuthorizer));
        }else {
            ret = Future.succeededFuture(noopAuthorizationService);
        }
        return ret;
    }
}
