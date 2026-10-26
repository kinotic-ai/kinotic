package org.kinotic.management.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.OrganizationMember;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.management.api.model.PendingInviteSummary;

/**
 * Member management for the caller's organization and its applications, used by the web app. Every
 * method derives the organization from the authenticated participant — only org members may
 * call, and a supplied {@code applicationId} must belong to that organization. The organization's members are
 * {@link OrganizationMember}s; an application's members are its {@link ApplicationParticipantIdentity}s.
 * Invitations are scoped: {@code applicationId} null addresses the organization's, set addresses that
 * application's.
 */
@Publish
public interface MemberService {

    /** Lists the caller's organization's members: its users. */
    Future<Page<OrganizationMember>> findMembers(Pageable pageable);

    /**
     * Searches the caller's organization's members by free text over email and display name. Blank
     * {@code searchText} is equivalent to {@link #findMembers}.
     */
    Future<Page<OrganizationMember>> searchMembers(String searchText, Pageable pageable);

    /** Lists the users of application {@code applicationId} of the caller's organization. */
    Future<Page<ApplicationParticipantIdentity>> findApplicationUsers(String applicationId, Pageable pageable);

    /**
     * Searches the users of application {@code applicationId} by free text over email and display name. Blank
     * {@code searchText} is equivalent to {@link #findApplicationUsers}.
     */
    Future<Page<ApplicationParticipantIdentity>> searchApplicationUsers(String searchText, String applicationId, Pageable pageable);

    /**
     * Adds a person to the caller's organization by email: a user of the organization with no sign-in method yet,
     * who appears among the members as pending. With {@code sendInvitation} an invitation is emailed, whose
     * acceptance claims the member with a password or through one of the organization's providers; without one the
     * person signs in through one of the organization's own providers, which claims the member on the first sign-in,
     * so the organization must have one. Fails for an address that is already a member, or with no invitation sent
     * when the organization has no provider of its own.
     *
     * @param email          the address the person signs in with
     * @param displayName    optional display name
     * @param sendInvitation whether to email an invitation
     * @return the member as listed, pending until they first sign in
     */
    Future<OrganizationMember> addMember(String email, String displayName, boolean sendInvitation);

    /**
     * Emails an invitation to a pending member of the caller's organization: the one they have, again, or a new
     * one for a member added without. Fails for an address that is not a pending member's.
     *
     * @param email the pending member's address
     */
    Future<Void> sendInvitation(String email);

    /**
     * Invites someone into one of the caller's organization's applications by email. Sends the invitation email
     * and returns the pending invitation, whose acceptance creates the application's user. Fails if the email
     * already has a user in the application or an invitation is already pending for it.
     *
     * @param email         where to send the invitation
     * @param displayName   optional display name for the invitee
     * @param applicationId the application to invite into, one of the caller's organization's
     */
    Future<PendingInviteSummary> inviteMember(String email, String displayName, String applicationId);

    /**
     * Enables or disables a member of the caller's organization or of one of its applications: a user of the
     * organization, or an application user. Disabling gates future logins to that scope;
     * established sessions last until they expire. Callers cannot disable themselves.
     *
     * @param identityId the member's id: an {@link OrganizationMember#id()} or an application user's id
     */
    Future<Void> setMemberEnabled(String identityId, boolean enabled);

    /**
     * Removes a member: a user of the caller's organization, along with the invitation it never accepted, or an
     * application user, each with its stored credentials. Callers cannot remove themselves.
     *
     * @param identityId the member's id: an {@link OrganizationMember#id()} or an application user's id
     */
    Future<Void> removeMember(String identityId);

    /**
     * Lists the scope's live (unexpired) pending invitations: for the organization, those of pending members sent one;
     * for an application, those of its invited users.
     */
    Future<Page<PendingInviteSummary>> findPendingInvites(String applicationId, Pageable pageable);

    /** Cancels a pending invitation belonging to the caller's organization. */
    Future<Void> cancelInvite(String inviteId);
}
