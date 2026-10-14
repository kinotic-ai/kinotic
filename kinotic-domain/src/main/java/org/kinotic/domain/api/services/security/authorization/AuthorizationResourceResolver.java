package org.kinotic.domain.api.services.security.authorization;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.security.authorization.AuthorizationScope;

/** Verifies ownership of a platform resource before its permission is checked. */
public interface AuthorizationResourceResolver {
    boolean supports(String resourceType);
    Future<Void> requireOwned(String resourceType, String resourceId, AuthorizationScope scope);
}
