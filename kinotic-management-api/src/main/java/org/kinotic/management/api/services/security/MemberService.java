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

    /** Lists the caller's organization's members: the organization users holding a membership of it. */
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
     * Adds a person to the caller's organization by email. An address with no organization user yet gets one,
     * with no sign-in method, and its membership at once, so the person appears among the members as pending. With
     * {@code sendInvitation} an invitation is emailed, whose acceptance claims the organization user with a password
     * or through one of the organization's providers; without one the person signs in through one of the
     * organization's own providers, which claims the organization user on the first sign-in, so the organization
     * must have one. An address that already has an organization user is emailed an invitation whatever
     * {@code sendInvitation} says, since joining it to an organization needs its holder's acceptance, and joins once
     * accepted. Fails for an address that is already a member, one an invitation is already pending for, or an
     * address with no organization user when no invitation is sent and the organization has no provider of its own.
     *
     * @param email          the address the person signs in with
     * @param displayName    optional display name
     * @param sendInvitation whether to email an invitation
     * @return the member as listed, pending until they first sign in; for an address that already has an organization user,
     *         a pending member with no id yet, which the list shows once the invitation is accepted
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
     * Enables or disables a member of the caller's organization or of one of its applications: an organization
     * user's membership of the organization, or an application user. Disabling gates future logins to that scope;
     * established sessions last until they expire. Callers cannot disable themselves.
     *
     * @param identityId the member's id: an {@link OrganizationMember#id()} or an application user's id
     */
    Future<Void> setMemberEnabled(String identityId, boolean enabled);

    /**
     * Removes a member: an organization user's membership of the caller's organization, leaving the organization
     * user and its other memberships as they are, unless no sign-in method signs it in and no other membership
     * remains, when it goes too, along with the invitation it never accepted; or an application user, with its
     * stored credential. Callers cannot remove themselves.
     *
     * @param identityId the member's id: an {@link OrganizationMember#id()} or an application user's id
     */
    Future<Void> removeMember(String identityId);

    /**
     * Lists the scope's live (unexpired) pending invitations: for the organization, those of people who already
     * had an organization user and of pending members sent one; for an application, those of its invited users.
     */
    Future<Page<PendingInviteSummary>> findPendingInvites(String applicationId, Pageable pageable);

    /** Cancels a pending invitation belonging to the caller's organization. */
    Future<Void> cancelInvite(String inviteId);

    /**
     * The accept link of the live invitation of {@code email} into the caller's organization or one of its
     * applications, for the inviter to send on when email is disabled ({@code kinotic.domain.email.enabled} is
     * {@code false}), as on a demonstration server. Anyone holding the link can accept the invitation as the invited
     * address.
     *
     * @param email         the invited address
     * @param applicationId the application invited into, one of the caller's organization's, or {@code null} for an
     *                      invitation into the organization
     * @return the link, or {@code null} when email is enabled or no invitation into the scope is pending for the
     *         address
     */
    Future<String> findInvitationLink(String email, String applicationId);
}
