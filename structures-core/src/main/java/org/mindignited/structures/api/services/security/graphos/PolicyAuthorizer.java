package org.mindignited.structures.api.services.security.graphos;

import org.mindignited.structures.api.domain.SecurityContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The {@link PolicyAuthorizer} is responsible for authorizing a list of {@link PolicyAuthorizationRequest}s
 */
public interface PolicyAuthorizer {

    CompletableFuture<Void> authorize(List<PolicyAuthorizationRequest> requests, SecurityContext securityContext);

}
