package org.kinotic.persistence.internal.api.services.security.graphos;


import org.kinotic.persistence.api.model.EntityContext;

import java.util.concurrent.CompletableFuture;

/**
 * Responsible for evaluating GraphOS policies for a given security context
 */
public interface PolicyEvaluator {

    /**
     * Evaluate the policies with the given security context
     *
     * @param entityContext the security context that is active for this current operation
     * @return a {@link CompletableFuture} that will complete with the {@link AuthorizationResult} of the evaluation
     */
    CompletableFuture<AuthorizationResult> evaluatePolicies(EntityContext entityContext);
}
