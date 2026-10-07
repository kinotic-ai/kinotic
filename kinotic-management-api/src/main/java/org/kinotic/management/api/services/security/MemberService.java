package org.kinotic.management.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.PendingInviteSummary;

/**
 * Member management for the caller's organization and its applications, used by the web app. Every
 * method derives the organization from the authenticated participant — only org members may
 * call, and a supplied {@code applicationId} must belong to that organization. Members and
 * invitations are scoped: {@code applicationId} null addresses org members, set addresses
 * that application's members.
 */
@Publish
@AuthzResource(value = AuthzUtil.ORGANIZATION_TYPE, resourceId = "{@organizationId}")
public interface MemberService {

    /** Lists the members of the scope. */
    @AuthzCheck(permission = AuthzUtil.CAN_VIEW_MEMBERS)
    Future<Page<UserParticipantIdentity>> findMembers(String applicationId, Pageable pageable);

    /**
     * Searches the scope's members by free text over email and display name. Blank
     * {@code searchText} is equivalent to {@link #findMembers}.
     */
    @AuthzCheck(permission = AuthzUtil.CAN_VIEW_MEMBERS)
    Future<Page<UserParticipantIdentity>> searchMembers(String searchText, String applicationId, Pageable pageable);

    /**
     * Invites someone into the scope by email. Sends the invitation email and returns the
     * pending invitation. Fails if the email already has an account in the scope or an
     * invitation is already pending for it.
     *
     * @param email       where to send the invitation
     * @param displayName optional display name for the invitee
     * @param applicationId the application to invite into, or null for an org-member invite
     * @param tenantId      the tenant of the application the invitee joins, which must exist, or null for an
     *                      org-member invite or an application invite into no tenant
     */
    @AuthzCheck(permission = AuthzUtil.CAN_MANAGE_MEMBERS)
    Future<PendingInviteSummary> inviteMember(String email, String displayName, String applicationId, String tenantId);

    /**
     * Enables or disables a member of the caller's organization. Disabling gates future
     * logins; established sessions last until they expire. Callers cannot disable themselves.
     */
    @AuthzCheck(permission = AuthzUtil.CAN_MANAGE_MEMBERS, consistent = true)
    Future<Void> setMemberEnabled(String identityId, boolean enabled);

    /**
     * Permanently removes a member of the caller's organization, including any stored
     * credential. Callers cannot remove themselves.
     */
    @AuthzCheck(permission = AuthzUtil.CAN_MANAGE_MEMBERS, consistent = true)
    Future<Void> removeMember(String identityId);

    /** Lists the scope's live (unexpired) pending invitations. */
    @AuthzCheck(permission = AuthzUtil.CAN_VIEW_MEMBERS)
    Future<Page<PendingInviteSummary>> findPendingInvites(String applicationId, Pageable pageable);

    /** Cancels a pending invitation belonging to the caller's organization. */
    @AuthzCheck(permission = AuthzUtil.CAN_MANAGE_MEMBERS)
    Future<Void> cancelInvite(String inviteId);
}
