package org.kinotic.domain.api.services.iam;

import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.IssuedRefreshToken;
import org.kinotic.domain.api.model.iam.RefreshTokenRotation;

import java.util.concurrent.CompletableFuture;

/**
 * Issues and rotates refresh tokens for CLI sessions. Tokens are stored only as hashes; each
 * redemption rotates the token and detects reuse of an already-rotated token.
 * <p>
 * Not a {@code @Publish} service — called in-process from the gateway's HTTP handlers.
 */
public interface RefreshTokenService {

    /**
     * Issues a new refresh token in a new family for the given user.
     *
     * @param userId id of the {@link IamUser} the token will authenticate
     */
    CompletableFuture<IssuedRefreshToken> issue(String userId);

    /**
     * Validates and rotates a refresh token: the presented token is revoked and a fresh one
     * issued in the same family. Presenting an already-revoked token revokes the whole family.
     * Fails for any invalid, expired, reused, or revoked token, and when the owning user no
     * longer exists or is disabled.
     *
     * @param token the plaintext refresh token presented by the client
     */
    CompletableFuture<RefreshTokenRotation> rotate(String token);
}
