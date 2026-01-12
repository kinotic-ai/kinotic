package org.mindignited.structures.api.services.security.graphos;

import lombok.RequiredArgsConstructor;
import org.kinotic.continuum.idl.api.schema.FunctionDefinition;
import org.mindignited.structures.api.domain.EntityOperation;
import org.mindignited.structures.api.domain.NamedQueryOperation;
import org.mindignited.structures.api.domain.Structure;
import org.mindignited.structures.api.domain.idl.decorators.PolicyDecorator;
import org.mindignited.structures.api.services.security.AuthorizationService;
import org.mindignited.structures.api.services.security.AuthorizationServiceFactory;
import org.mindignited.structures.internal.api.services.impl.security.NoopAuthorizationService;

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
