package org.mindignited.structures.api.services.security.graphos;

import org.mindignited.continuum.api.exceptions.AuthorizationException;
import org.mindignited.continuum.idl.api.schema.FunctionDefinition;
import org.mindignited.structures.api.domain.NamedQueryOperation;
import org.mindignited.structures.api.domain.SecurityContext;
import org.mindignited.structures.api.domain.idl.decorators.PolicyDecorator;
import org.mindignited.structures.api.services.security.AuthorizationService;
import org.mindignited.structures.internal.api.services.impl.security.graphos.DefaultPolicyAuthorizationRequest;
import org.mindignited.structures.internal.api.services.impl.security.graphos.PolicyExpression;
import org.mindignited.structures.internal.api.services.impl.security.graphos.PolicyExpressionUtil;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * This {@link AuthorizationService} will authorize Named Query execution requests
 * Created By Navid Mitchell on 12/31/24
 */
public class NamedQueryPolicyAuthorizationService implements AuthorizationService<NamedQueryOperation> {

    private final PolicyAuthorizer policyAuthorizer;
    /**
     * The {@link PolicyExpression} provided by the named query
     */
    private final PolicyExpression policyExpression;
    /**
     * The set of policies that are required to execute the named query
     */
    private final Set<String> allPolicies = new HashSet<>();

    public NamedQueryPolicyAuthorizationService(FunctionDefinition namedQuery,
                                                PolicyAuthorizer policyAuthorizer) {
        this.policyAuthorizer = policyAuthorizer;
        PolicyDecorator policyDecorator = namedQuery.findDecorator(PolicyDecorator.class);
        if(policyDecorator == null){
            throw new IllegalArgumentException("the Named Query must contain contain a PolicyDecorator");
        }
        this.policyExpression = PolicyExpressionUtil.createPolicyExpression(policyDecorator.getPolicies());
        PolicyExpressionUtil.collectPolicies(this.policyExpression, allPolicies);
    }


    @Override
    public CompletableFuture<Void> authorize(NamedQueryOperation operationIdentifier, SecurityContext securityContext) {

        Map<String, PolicyAuthorizationRequest> policyRequests = allPolicies.stream()
                                                                            .collect(Collectors.toMap(policy -> policy, DefaultPolicyAuthorizationRequest::new));

        List<PolicyAuthorizationRequest> requests = new ArrayList<>(policyRequests.values());

        return policyAuthorizer.authorize(requests, securityContext)
                               .thenCompose(ignored -> {

                                   boolean queryAllowed = this.policyExpression.evaluate(policyRequests);
                                   if(queryAllowed){
                                       return CompletableFuture.completedFuture(null);
                                   }else{
                                       return CompletableFuture.failedFuture(new AuthorizationException("The Named Query is not authorized"));

                                   }
                               });
    }
}
