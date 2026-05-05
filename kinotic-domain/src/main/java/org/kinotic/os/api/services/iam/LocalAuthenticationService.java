package org.kinotic.os.api.services.iam;

import org.kinotic.os.api.model.iam.IamUser;

import java.util.concurrent.CompletableFuture;

/**
 * In-process service for verifying email + password and resolving the matching
 * {@link IamUser}. Not {@code @Publish}-annotated: raw passwords never travel over RPC,
 * only direct in-JVM calls (e.g. the {@code POST /api/login/token} HTTP handler) or
 * STOMP CONNECT credentials handled separately by the {@code SecurityService}.
 */
public interface LocalAuthenticationService {

    /**
     * Verifies {@code password} against the {@link IamUser} matching {@code email}
     * across any scope. Returns the user on success, or {@code null} for any failure
     * (unknown email, wrong password, OIDC user, disabled user). Callers should surface
     * a generic message to the client to avoid leaking which case applies.
     *
     * <p>Used by the org-login token endpoint, which intentionally accepts both
     * ORGANIZATION-scope users and the SYSTEM-scope dev admin.
     */
    CompletableFuture<IamUser> authenticateLocal(String email, String password);

    /**
     * Scope-restricted variant of {@link #authenticateLocal(String, String)}: only
     * matches an {@link IamUser} in the given {@code (authScopeType, authScopeId)} pair.
     * Used by the application and system login handlers so a stray cross-scope match
     * (e.g. the dev admin row in SYSTEM scope) can't authenticate against an app or
     * system endpoint.
     */
    CompletableFuture<IamUser> authenticateLocal(String email, String password,
                                                 String authScopeType, String authScopeId);
}
