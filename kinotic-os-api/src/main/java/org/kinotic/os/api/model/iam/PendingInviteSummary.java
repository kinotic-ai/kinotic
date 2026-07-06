package org.kinotic.os.api.model.iam;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * View of a pending invitation for listing in the web app. Deliberately excludes the accept token — anyone who
 * can list invitations must not be able to redeem them.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class PendingInviteSummary {

    private String id;

    /** Email the invitation was sent to. */
    private String email;

    /** Display name the inviter entered, if any. */
    private String displayName;

    /** The Application the invitee would join; {@code null} for an organization-member invite. */
    private String applicationId;

    /** Display name of the member who sent the invitation. */
    private String invitedByName;

    private Date created;

    /** When the invitation stops being acceptable. */
    private Date expiresAt;
}
