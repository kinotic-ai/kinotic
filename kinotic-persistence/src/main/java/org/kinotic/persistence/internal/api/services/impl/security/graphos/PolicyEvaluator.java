package org.kinotic.persistence.internal.api.services.impl.security.graphos;

import org.kinotic.structures.api.domain.SecurityContext;

import java.util.concurrent.CompletableFuture;

/**
 * Responsible for evaluating GraphOS policies for a given security context
 */
public interface PolicyEvaluator {

    /**
     * Evaluate the policies with the given security context
     * @param securityContext the security context that is active for this current operation
     * @return a {@link CompletableFuture} that will complete with the {@link AuthorizationResult} of the evaluation
     */
    CompletableFuture<AuthorizationResult> evaluatePolicies(SecurityContext securityContext);
}
