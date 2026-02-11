package org.kinotic.persistence.internal.api.services.impl.security.graphos;

import org.kinotic.persistence.api.services.security.graphos.PolicyAuthorizationRequest;

import java.util.List;
import java.util.Map;

/**
 * Represents an OR expression in a policy expression tree
 * @param expressions the expressions to OR together
 */
public record OrExpression(List<PolicyExpression> expressions) implements PolicyExpression {

    @Override
    public boolean evaluate(Map<String, PolicyAuthorizationRequest> policyRequests) {
        return expressions.stream().anyMatch(expression -> expression.evaluate(policyRequests));
    }
}
