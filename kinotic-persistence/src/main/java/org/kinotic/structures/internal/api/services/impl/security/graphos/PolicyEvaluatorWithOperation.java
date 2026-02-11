package org.kinotic.structures.internal.api.services.impl.security.graphos;

import org.kinotic.structures.api.services.security.graphos.PolicyAuthorizationRequest;
import org.kinotic.structures.api.services.security.graphos.PolicyAuthorizer;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A PolicyEvaluator that supports policies for a given operation as well as entity policies
 */
public class PolicyEvaluatorWithOperation extends AbstractPolicyEvaluator {
    private final PolicyExpression operationExpression;
    private final Set<String> operationPolicies = new HashSet<>();

    public PolicyEvaluatorWithOperation(
            PolicyAuthorizer authorizer,
            SharedPolicyManager sharedPolicyManager,
            List<List<String>> operationPolicies) {

        super(authorizer, sharedPolicyManager);

        if (operationPolicies != null) {
            this.operationExpression = PolicyExpressionUtil.createPolicyExpression(operationPolicies);
            PolicyExpressionUtil.collectPolicies(this.operationExpression, this.operationPolicies);
        } else {
            this.operationExpression = null;
        }
    }

    @Override
    protected void addOperationPolicies(Set<String> policies) {
        policies.addAll(operationPolicies);
    }

    @Override
    protected boolean isOperationAllowed(Map<String, PolicyAuthorizationRequest> policyRequests) {
        return operationExpression == null || operationExpression.evaluate(policyRequests);
    }
}
