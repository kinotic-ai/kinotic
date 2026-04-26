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
     * Verifies {@code password} against the primary {@link IamUser} for the given
     * {@code email}. Returns the user on success, or {@code null} for any failure
     * (unknown email, wrong password, OIDC user, disabled user). Callers should
     * surface a generic message to the client to avoid leaking which case applies.
     */
    CompletableFuture<IamUser> authenticateLocal(String email, String password);
}
