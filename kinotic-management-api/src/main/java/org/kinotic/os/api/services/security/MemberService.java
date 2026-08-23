package org.kinotic.os.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.UserParticipantIdentity;
import org.kinotic.os.api.model.security.PendingInviteSummary;

/**
 * Member management for the caller's organization and its applications, used by the web app. Every
 * method derives the organization from the authenticated participant — only org members may
 * call, and a supplied {@code applicationId} must belong to that organization. Members and
 * invitations are scoped: {@code applicationId} null addresses org members, set addresses
 * that application's members.
 */
@Publish
public interface MemberService {

    /** Lists the members of the scope. */
    Future<Page<UserParticipantIdentity>> findMembers(String applicationId, Pageable pageable);

    /**
     * Searches the scope's members by free text over email and display name. Blank
     * {@code searchText} is equivalent to {@link #findMembers}.
     */
    Future<Page<UserParticipantIdentity>> searchMembers(String searchText, String applicationId, Pageable pageable);

    /**
     * Invites someone into the scope by email. Sends the invitation email and returns the
     * pending invitation. Fails if the email already has an account in the scope or an
     * invitation is already pending for it.
     *
     * @param email       where to send the invitation
     * @param displayName optional display name for the invitee
     * @param applicationId the application to invite into, or null for an org-member invite
     */
    Future<PendingInviteSummary> inviteMember(String email, String displayName, String applicationId);

    /**
     * Enables or disables a member of the caller's organization. Disabling gates future
     * logins; established sessions last until they expire. Callers cannot disable themselves.
     */
    Future<Void> setMemberEnabled(String identityId, boolean enabled);

    /**
     * Permanently removes a member of the caller's organization, including any stored
     * credential. Callers cannot remove themselves.
     */
    Future<Void> removeMember(String identityId);

    /** Lists the scope's live (unexpired) pending invitations. */
    Future<Page<PendingInviteSummary>> findPendingInvites(String applicationId, Pageable pageable);

    /** Cancels a pending invitation belonging to the caller's organization. */
    Future<Void> cancelInvite(String inviteId);
}
