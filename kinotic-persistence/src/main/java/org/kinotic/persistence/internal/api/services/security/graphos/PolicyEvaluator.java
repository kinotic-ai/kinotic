package org.kinotic.persistence.internal.api.services.security.graphos;


import io.vertx.core.Future;
import org.kinotic.persistence.api.model.EntityContext;

/**
 * Responsible for evaluating GraphOS policies for a given security context
 */
public interface PolicyEvaluator {

    /**
     * Evaluate the policies with the given security context
     *
     * @param entityContext the security context that is active for this current operation
     * @return a {@link Future} that will complete with the {@link AuthorizationResult} of the evaluation
     */
    Future<AuthorizationResult> evaluatePolicies(EntityContext entityContext);
}
