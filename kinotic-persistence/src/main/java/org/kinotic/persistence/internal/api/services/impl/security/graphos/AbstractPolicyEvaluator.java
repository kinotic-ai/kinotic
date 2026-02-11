package org.kinotic.persistence.internal.api.services.impl.security.graphos;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.kinotic.persistence.api.domain.SecurityContext;
import org.kinotic.persistence.api.services.security.graphos.PolicyAuthorizationRequest;
import org.kinotic.persistence.api.services.security.graphos.PolicyAuthorizer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public abstract class AbstractPolicyEvaluator implements PolicyEvaluator {
    protected final PolicyAuthorizer authorizer;
    protected final SharedPolicyManager sharedPolicyManager;

    public AbstractPolicyEvaluator(PolicyAuthorizer authorizer, SharedPolicyManager sharedPolicyManager) {
        this.authorizer = authorizer;
        this.sharedPolicyManager = sharedPolicyManager;
    }

    @WithSpan
    @Override
    public CompletableFuture<AuthorizationResult> evaluatePolicies(SecurityContext securityContext) {
        Set<String> allPolicies = new HashSet<>(sharedPolicyManager.getSharedPolicies());
        addOperationPolicies(allPolicies);

        // no need to call authorizer if there are no policies to evaluate
        if (allPolicies.isEmpty()) {

            return CompletableFuture.completedFuture(new AuthorizationResult(true, true, Collections.emptyMap()));

        }else {
            Map<String, PolicyAuthorizationRequest> policyRequests = allPolicies.stream()
                                                                                .collect(Collectors.toMap(policy -> policy,
                                                                                                          DefaultPolicyAuthorizationRequest::new));
            List<PolicyAuthorizationRequest> requests = new ArrayList<>(policyRequests.values());

            return authorizer.authorize(requests, securityContext)
                             .thenApply(ignored -> {

                                 Map<String, Boolean> fieldResults = evaluateFieldPolicies(policyRequests);

                                 boolean operationAllowed = isOperationAllowed(policyRequests);

                                 boolean entityAllowed = sharedPolicyManager.getEntityExpression() == null
                                         || sharedPolicyManager.getEntityExpression().evaluate(policyRequests);

                                 return new AuthorizationResult(operationAllowed, entityAllowed, fieldResults);
                             });
        }
    }

    private Map<String, Boolean> evaluateFieldPolicies(Map<String, PolicyAuthorizationRequest> policyRequests) {
        Map<String, Boolean> fieldResults = new HashMap<>();
        for (Map.Entry<String, PolicyExpression> entry : sharedPolicyManager.getFieldExpressions().entrySet()) {
            String fieldName = entry.getKey();
            PolicyExpression expression = entry.getValue();
            fieldResults.put(fieldName, expression.evaluate(policyRequests));
        }
        return fieldResults;
    }

    // Abstract methods to be implemented by subclasses
    /**
     * Add operation specific policies to the provided set
     * @param policies the set to add operation specific policies to
     */
    protected abstract void addOperationPolicies(Set<String> policies);

    /**
     * Determine if the operation is allowed based on the provided policy requests
     * @param policyRequests the policy requests to evaluate
     * @return true if the operation is allowed
     */
    protected abstract boolean isOperationAllowed(Map<String, PolicyAuthorizationRequest> policyRequests);
}




