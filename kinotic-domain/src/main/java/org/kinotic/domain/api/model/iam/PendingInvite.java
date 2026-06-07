package org.kinotic.domain.api.model.iam;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * A short-lived, single-use record capturing a member invitation in flight — held from the moment
 * an administrator invites someone into an <em>existing</em> scope until the invitee accepts, at
 * which point it is consumed to create the {@link IamUser} at that scope, then deleted.
 * <p>
 * Unlike {@link PendingSignUp}, which creates a brand-new organization and its admin, an invite
 * targets a scope that already exists: {@link #organizationId} is always set, and
 * {@link #applicationId} is set for an application-scope invite (null for an organization-scope
 * invite).
 * <p>
 * {@link #authType} records how the invitee will establish their identity: LOCAL (they set a
 * password when accepting) or OIDC (they sign in through the scope's configured provider, recorded
 * in {@link #oidcConfigId}, and the IdP-verified email must match {@link #email}).
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class PendingInvite implements PendingVerification {

    private String id;

    /** Opaque single-use token carried in the acceptance URL. */
    private String verificationToken;

    /** Hard expiry after which the token is invalid. */
    private Date expiresAt;

    private Date created;

    /** Email the eventual {@link IamUser} is created with; the invite is addressed here. */
    private String email;

    /** Display name for the eventual {@link IamUser}. */
    private String displayName;

    /** How the invitee establishes identity — drives whether a password is set on acceptance. */
    private AuthType authType;

    /** The organization the new member joins. Always set. */
    private String organizationId;

    /** The application the new member joins, or null for an organization-scope invite. */
    private String applicationId;

    /** Id of the OIDC configuration the invitee signs in through; null for LOCAL. */
    private String oidcConfigId;

    /** Id of the {@link IamUser} who issued the invitation. */
    private String invitedBy;
}
