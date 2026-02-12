package org.kinotic.persistence.internal.api.services.security.graphos;

import org.kinotic.persistence.api.services.security.graphos.PolicyAuthorizationRequest;
import org.kinotic.persistence.api.services.security.graphos.PolicyAuthorizer;

import java.util.Map;

/**
 * Represents a GraphOS policy expression , i.e. [['entity:add'], ['entity:update']]
 */
public interface PolicyExpression {

    /**
     * Evaluate the policy expression, by taking a map of {@link PolicyAuthorizationRequest}s that have already been passed to a {@link PolicyAuthorizer}
     * These are then compared to the policy expression to determine if the expression evaluates to true.
     * @param policyRequests a map of pre-authorized {@link PolicyAuthorizationRequest}s to evaluate keyed by the policy name
     * @return true if the policy expression evaluates to true
     */
    boolean evaluate(Map<String, PolicyAuthorizationRequest> policyRequests);

}
