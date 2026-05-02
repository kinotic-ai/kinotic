package org.kinotic.core.api.security;

/**
 * Well-known IAM scope layers. Each layer maintains completely separate user pools.
 * SYSTEM is for platform operators, ORGANIZATION is for development teams,
 * and APPLICATION is for end-users consuming deployed applications.
 * <p>
 * Carried on {@link Participant#getAuthScopeType()} and consumed by
 * {@link SecurityContext#requireAuthScope(AuthScopeType)} for callers that need
 * to gate access to a particular scope.
 */
public enum AuthScopeType {
    SYSTEM,
    ORGANIZATION,
    APPLICATION
}
