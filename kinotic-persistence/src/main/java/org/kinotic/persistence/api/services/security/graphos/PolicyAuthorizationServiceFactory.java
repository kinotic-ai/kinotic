package org.kinotic.persistence.api.services.security.graphos;

import lombok.RequiredArgsConstructor;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.persistence.api.model.EntityDefinition;
import org.kinotic.persistence.api.model.EntityOperation;
import org.kinotic.persistence.api.model.NamedQueryOperation;
import org.kinotic.persistence.api.model.idl.decorators.PolicyDecorator;
import org.kinotic.persistence.api.services.security.AuthorizationService;
import org.kinotic.persistence.api.services.security.AuthorizationServiceFactory;
import org.kinotic.persistence.internal.api.services.security.NoopAuthorizationService;

import java.util.concurrent.CompletableFuture;

/**
 * Created By Navíd Mitchell 🤪on 12/31/24
 */
@RequiredArgsConstructor
public class PolicyAuthorizationServiceFactory implements AuthorizationServiceFactory {

    private final PolicyAuthorizer policyAuthorizer;
    private final NoopAuthorizationService<NamedQueryOperation> noopAuthorizationService = new NoopAuthorizationService<>();

    @Override
    public CompletableFuture<AuthorizationService<EntityOperation>> createEntityDefinitionAuthorizationService(EntityDefinition entityDefinition) {
        return CompletableFuture.completedFuture(new EntityDefinitionPolicyAuthorizationService(entityDefinition, policyAuthorizer));
    }

    @Override
    public CompletableFuture<AuthorizationService<NamedQueryOperation>> createNamedQueryAuthorizationService(FunctionDefinition namedQuery) {
        if(namedQuery.containsDecorator(PolicyDecorator.class)) {
            return CompletableFuture.completedFuture(new NamedQueryPolicyAuthorizationService(namedQuery, policyAuthorizer));
        }else {
            return CompletableFuture.completedFuture(noopAuthorizationService);
        }
    }
}
