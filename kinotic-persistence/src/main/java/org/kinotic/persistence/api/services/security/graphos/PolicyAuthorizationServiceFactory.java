package org.kinotic.persistence.api.services.security.graphos;

import lombok.RequiredArgsConstructor;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.persistence.api.domain.EntityOperation;
import org.kinotic.persistence.api.domain.NamedQueryOperation;
import org.kinotic.persistence.api.domain.Structure;
import org.kinotic.persistence.api.domain.idl.decorators.PolicyDecorator;
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
    public CompletableFuture<AuthorizationService<EntityOperation>> createStructureAuthorizationService(Structure structure) {
        return CompletableFuture.completedFuture(new StructurePolicyAuthorizationService(structure, policyAuthorizer));
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
