package org.kinotic.domain.api.services.security.authorization;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.security.authorization.AuthorizationScope;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;

/** Checks a resource permission using the authenticated scope and active application model. */
public interface PermissionAuthorizationService {
    Future<Void> require(ScopedParticipant participant, AuthorizationScope scope, String resourceType, String resourceId, String permission);
}
