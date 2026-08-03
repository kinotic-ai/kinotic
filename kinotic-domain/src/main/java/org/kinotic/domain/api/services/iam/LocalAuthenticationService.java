package org.kinotic.domain.api.services.iam;

import org.kinotic.domain.api.model.iam.UserParticipantIdentity;

import java.util.concurrent.CompletableFuture;

/**
 * In-process service for verifying email + password and resolving the matching
 * {@link UserParticipantIdentity}. Not {@code @Publish}-annotated: raw passwords never travel over RPC,
 * only direct in-JVM calls (e.g. the {@code POST /api/auth/org/login} HTTP handler) or
 * STOMP CONNECT credentials handled separately by the {@code SecurityService}.
 */
public interface LocalAuthenticationService {

    /**
     * Verifies {@code password} against the {@link UserParticipantIdentity} matching {@code email}
     * across any scope. Returns the user on success, or {@code null} for any failure
     * (unknown email, wrong password, OIDC user, disabled user). Callers should surface
     * a generic message to the client to avoid leaking which case applies.
     *
     * <p>Used by the org-login token endpoint, which intentionally accepts both
     * ORGANIZATION-scope users and the SYSTEM-scope dev admin.
     */
    CompletableFuture<UserParticipantIdentity> authenticateLocal(String email, String password);

    /**
     * Scope-restricted variant of {@link #authenticateLocal(String, String)}: only
     * matches an {@link UserParticipantIdentity} in the given {@code (organizationId, applicationId)}
     * pair. Used by the application and system login handlers so a stray cross-scope
     * match (e.g. the dev admin row in SYSTEM scope) can't authenticate against an app
     * or system endpoint. Scope is identified structurally:
     * <ul>
     *   <li>both null → SYSTEM</li>
     *   <li>{@code organizationId} only → ORGANIZATION</li>
     *   <li>both set → APPLICATION</li>
     * </ul>
     */
    CompletableFuture<UserParticipantIdentity> authenticateLocal(String email, String password,
                                                 String organizationId, String applicationId);
}

