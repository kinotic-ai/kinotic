package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;

/**
 * In-process service for verifying email + password and resolving the matching
 * {@link UserParticipantIdentity}. Not {@code @Publish}-annotated: raw passwords never travel over RPC,
 * only direct in-JVM calls (e.g. the {@code POST /api/auth/org/login/:orgId} HTTP handler) or
 * STOMP CONNECT credentials handled separately by the {@code SecurityService}.
 */
public interface LocalAuthenticationService {

    /**
     * Verifies {@code password} against the {@link UserParticipantIdentity} matching {@code email} in the
     * given {@code (organizationId, applicationId)} pair, so a match in another scope can't authenticate.
     * Returns the user on success, or {@code null} for any failure (unknown email, wrong password, a user
     * with no password, disabled user); callers surface a generic message so the client learns nothing
     * about which case applies. An account found this way acts through one of its memberships, which the
     * caller resolves. Scope is identified structurally:
     * <ul>
     *   <li>both null → an account</li>
     *   <li>{@code organizationId} only → ORGANIZATION</li>
     *   <li>both set → APPLICATION</li>
     * </ul>
     */
    Future<UserParticipantIdentity> authenticateLocal(String email, String password,
                                                      String organizationId, String applicationId);
}

