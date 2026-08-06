package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * A short-lived, single-use record capturing an invitation into an existing scope — an
 * Organization, or an Application within one. Created by an org member, consumed when the
 * invitee accepts (creating the new {@link ParticipantIdentity} in the stored scope), then deleted.
 * <p>
 * Unlike {@link PendingSignUp} there is no {@code authType}: the invitee chooses how to
 * accept — set a password or sign in with an OIDC provider configured for the scope — and
 * the created user's auth type follows from that choice.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class PendingInvite implements PendingVerification {

    private String id;

    /** Opaque single-use token carried in the accept URL. */
    private String verificationToken;

    /** Hard expiry after which the token is invalid. */
    private Date expiresAt;

    private Date created;

    /** Email the invitation was sent to; the eventual {@link ParticipantIdentity} is created with it. */
    private String email;

    /** Display name for the eventual {@link ParticipantIdentity}; the invitee may override it at accept. */
    private String displayName;

    /** The Organization the invitee joins. Always set. */
    private String organizationId;

    /** The Application the invitee joins; {@code null} for an organization-member invite. */
    private String applicationId;

    /** Id of the member who sent the invitation. */
    private String invitedById;

    /** Display name of the member who sent the invitation, shown in the email and accept page. */
    private String invitedByName;
}
