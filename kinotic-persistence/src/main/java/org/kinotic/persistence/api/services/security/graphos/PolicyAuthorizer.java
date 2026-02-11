package org.kinotic.persistence.api.services.security.graphos;

import org.kinotic.persistence.api.domain.SecurityContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The {@link PolicyAuthorizer} is responsible for authorizing a list of {@link PolicyAuthorizationRequest}s
 */
public interface PolicyAuthorizer {

    CompletableFuture<Void> authorize(List<PolicyAuthorizationRequest> requests, SecurityContext securityContext);

}
