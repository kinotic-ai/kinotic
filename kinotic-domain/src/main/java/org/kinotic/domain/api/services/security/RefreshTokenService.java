package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.security.DelegateSession;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.KinoticAudience;
import org.kinotic.domain.api.model.security.RefreshTokenRotation;

import java.util.List;

/**
 * Issues and rotates refresh tokens for OAuth client sessions. Tokens are stored only as hashes;
 * each redemption rotates the token and detects reuse of an already-rotated token. A lineage
 * records the audience it was issued for, so rotation stamps replacements with that same
 * audience.
 */
public interface RefreshTokenService {

    /**
     * Issues a new refresh token in a new family for the given identity.
     *
     * @param identityId id of the {@link ParticipantIdentity} the token will authenticate
     * @param audience   the surface access tokens minted from this lineage are valid for
     * @param label      optional human-readable label for the family (e.g. a device name),
     *                   shown wherever the identity's sessions are listed
     * @return the plaintext refresh token — available only here, the server stores only its hash
     */
    Future<String> issue(String identityId, KinoticAudience audience, String label);

    /**
     * Validates and rotates a refresh token: the presented token is revoked and a fresh one
     * issued in the same family. Presenting an already-revoked token revokes the whole family.
     * Fails for any invalid, expired, reused, or revoked token, and when the owning identity no
     * longer exists or is disabled.
     *
     * @param token the plaintext refresh token presented by the client
     */
    Future<RefreshTokenRotation> rotate(String token);

    /**
     * Lists the identity's live sessions — one per family whose current token is unrevoked
     * and unexpired.
     */
    Future<List<DelegateSession>> findActiveSessions(String identityId);

    /**
     * Revokes every token in the given family, ending that session; future rotations of it
     * fail. Scoped by identity so a family can only be revoked through the identity it
     * authenticates.
     *
     * @param identityId id of the identity the family authenticates
     * @param familyId   the family to revoke
     */
    Future<Void> revokeFamily(String identityId, String familyId);

    /**
     * Revokes every live family of the given identity, ending all of its sessions.
     */
    Future<Void> revokeAllFor(String identityId);
}
