package org.mindignited.structures.internal.api.services.impl.security.graphos;

import org.mindignited.structures.api.services.security.graphos.PolicyAuthorizationRequest;

import java.util.Map;

/**
 * Represents a leaf node in a policy expression tree
 * @param policy the policy to evaluate
 */
public record LeafExpression(String policy) implements PolicyExpression {

    @Override
    public boolean evaluate(Map<String, PolicyAuthorizationRequest> policyRequests) {
        PolicyAuthorizationRequest request = policyRequests.get(policy);
        return request != null && request.isAuthorized();
    }

}
