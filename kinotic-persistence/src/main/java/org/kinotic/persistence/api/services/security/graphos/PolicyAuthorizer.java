package org.kinotic.persistence.api.services.security.graphos;

import io.vertx.core.Future;
import org.kinotic.persistence.api.model.EntityContext;

import java.util.List;

/**
 * The {@link PolicyAuthorizer} is responsible for authorizing a list of {@link PolicyAuthorizationRequest}s
 */
public interface PolicyAuthorizer {

    Future<Void> authorize(List<PolicyAuthorizationRequest> requests, EntityContext entityContext);

}
