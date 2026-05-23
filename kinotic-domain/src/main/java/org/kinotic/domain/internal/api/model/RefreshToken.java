package org.kinotic.domain.internal.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * A rotating refresh token for a CLI session. Each redemption revokes this record and issues
 * a replacement in the same {@link #familyId} lineage; presenting an already-revoked token
 * (reuse) revokes the entire family.
 * <p>
 * Internal-only — never published. Only ever holds a SHA-256 hash of the token, never the
 * plaintext.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class RefreshToken implements Identifiable<String> {

    private String id;

    /** SHA-256 hash of the refresh token. The plaintext is returned to the client once and never stored. */
    private String tokenHash;

    /** Id of the {@link org.kinotic.domain.api.model.iam.IamUser} this token authenticates. */
    private String userId;

    /** Groups every token in one rotation lineage so that reuse can revoke the whole family. */
    private String familyId;

    private Date created;

    /** Hard expiry after which the token can no longer be redeemed. */
    private Date expiresAt;

    /** When this token was redeemed (rotated), or {@code null} if it was never used. */
    private Date lastUsedAt;

    /** {@code true} once this token has been rotated or revoked. */
    private boolean revoked;

    /** Id of the {@link RefreshToken} that replaced this one on rotation, or {@code null} if not yet rotated. */
    private String replacedById;
}
