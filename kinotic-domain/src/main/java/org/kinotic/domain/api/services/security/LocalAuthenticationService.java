package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;

/**
 * In-process service for verifying email + password and resolving the matching
 * {@link UserParticipantIdentity}. Not {@code @Publish}-annotated: raw passwords never travel over RPC,
 * only direct in-JVM calls (e.g. the {@code POST /api/auth/org/login} HTTP handler) or
 * STOMP CONNECT credentials handled separately by the {@code SecurityService}.
 */
public interface LocalAuthenticationService {

    /**
     * Verifies {@code password} against the ORGANIZATION-scope {@link UserParticipantIdentity} matching
     * {@code email}, in whichever organization it belongs to. Returns the user on success, or {@code null}
     * for any failure (unknown email, wrong password, OIDC user, disabled user). Callers should surface
     * a generic message to the client to avoid leaking which case applies.
     */
    Future<UserParticipantIdentity> authenticateOrgUser(String email, String password);

    /**
     * Verifies {@code password} against the {@link UserParticipantIdentity} matching {@code email} in the
     * given {@code (organizationId, applicationId)} pair, so a match in another scope can't authenticate.
     * Returns the user on success, or {@code null} for any failure. Scope is identified structurally:
     * <ul>
     *   <li>both null → SYSTEM</li>
     *   <li>{@code organizationId} only → ORGANIZATION</li>
     *   <li>both set → APPLICATION</li>
     * </ul>
     */
    Future<UserParticipantIdentity> authenticateLocal(String email, String password,
                                                      String organizationId, String applicationId);
}

