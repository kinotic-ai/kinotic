package org.kinotic.structures.internal.api.services.impl.security.graphos;

import org.kinotic.structures.api.services.security.graphos.PolicyAuthorizationRequest;

import java.util.List;
import java.util.Map;

/**
 * Represents an AND expression in a policy expression tree
 * @param expressions the expressions to AND together
 */
public record AndExpression(List<PolicyExpression> expressions) implements PolicyExpression {

    @Override
    public boolean evaluate(Map<String, PolicyAuthorizationRequest> policyRequests) {
        return expressions.stream().allMatch(expression -> expression.evaluate(policyRequests));
    }

}
