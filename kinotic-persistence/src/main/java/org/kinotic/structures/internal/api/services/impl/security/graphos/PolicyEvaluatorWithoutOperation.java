package org.kinotic.structures.internal.api.services.impl.security.graphos;

import org.kinotic.structures.api.services.security.graphos.PolicyAuthorizationRequest;
import org.kinotic.structures.api.services.security.graphos.PolicyAuthorizer;

import java.util.Map;
import java.util.Set;

/**
 * A PolicyEvaluator that does not evaluate any operation
 * This is useful when a policy evaluator is only concerned with evaluating shared policies
 * and does not need to evaluate operation policies
 */
public class PolicyEvaluatorWithoutOperation extends AbstractPolicyEvaluator {

    public PolicyEvaluatorWithoutOperation(PolicyAuthorizer authorizer, SharedPolicyManager sharedPolicyManager) {
        super(authorizer, sharedPolicyManager);
    }

    @Override
    protected void addOperationPolicies(Set<String> policies) {
        // No operation policies to add
    }

    @Override
    protected boolean isOperationAllowed(Map<String, PolicyAuthorizationRequest> policyRequests) {
        return true; // Always allow operations since none are defined
    }
}

