package org.kinotic.management.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.Member;
import org.kinotic.management.api.model.PendingInviteSummary;

/**
 * Member management for the caller's organization and its applications, used by the web app. Every
 * method derives the organization from the authenticated participant — only org members may
 * call, and a supplied {@code applicationId} must belong to that organization. Members and
 * invitations are scoped: {@code applicationId} null addresses org members, set addresses
 * that application's members.
 */
@Publish
public interface MemberService {

    /**
     * Lists the members of the scope: the accounts holding a membership of the caller's organization, or
     * the users of one of its applications.
     */
    Future<Page<Member>> findMembers(String applicationId, Pageable pageable);

    /**
     * Searches the scope's members by free text over email and display name. Blank
     * {@code searchText} is equivalent to {@link #findMembers}.
     */
    Future<Page<Member>> searchMembers(String searchText, String applicationId, Pageable pageable);

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
     * Enables or disables a member of the caller's organization or of one of its applications: an account's
     * membership of the organization, or an application user. Disabling gates future logins to that scope;
     * established sessions last until they expire. Callers cannot disable themselves.
     *
     * @param identityId the member's id, as {@link Member#id()} names it
     */
    Future<Void> setMemberEnabled(String identityId, boolean enabled);

    /**
     * Removes a member: an account's membership of the caller's organization, leaving the account and its other
     * memberships as they are, or an application user, with its stored credential. Callers cannot remove
     * themselves.
     *
     * @param identityId the member's id, as {@link Member#id()} names it
     */
    Future<Void> removeMember(String identityId);

    /** Lists the scope's live (unexpired) pending invitations. */
    Future<Page<PendingInviteSummary>> findPendingInvites(String applicationId, Pageable pageable);

    /** Cancels a pending invitation belonging to the caller's organization. */
    Future<Void> cancelInvite(String inviteId);
}
