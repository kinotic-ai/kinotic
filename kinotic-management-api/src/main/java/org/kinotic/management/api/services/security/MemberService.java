package org.kinotic.management.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.management.api.model.AddApplicationUserRequest;
import org.kinotic.management.api.model.PendingInviteSummary;

/**
 * Member management for the caller's organization and its applications, used by the web app. Every
 * method derives the organization from the authenticated participant — only org members may
 * call, and a supplied {@code applicationId} must belong to that organization. The organization's members are
 * its {@link OrganizationParticipantIdentity}s; an application's members are its
 * {@link ApplicationParticipantIdentity}s. Each is {@code pending} until it first signs in.
 * Invitations are scoped: {@code applicationId} null addresses the organization's, set addresses that
 * application's.
 */
@Publish
@AuthzResource(value = AuthzConstants.ORGANIZATION_TYPE, resourceId = "{@organizationId}")
public interface MemberService {

    /** Lists the caller's organization's members: its users. */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_MEMBERS)
    Future<Page<OrganizationParticipantIdentity>> findMembers(Pageable pageable);

    /**
     * Searches the caller's organization's members by free text over email and display name. Blank
     * {@code searchText} is equivalent to {@link #findMembers}.
     */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_MEMBERS)
    Future<Page<OrganizationParticipantIdentity>> searchMembers(String searchText, Pageable pageable);

    /** Lists the users of application {@code applicationId} of the caller's organization. */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_MEMBERS)
    Future<Page<ApplicationParticipantIdentity>> findApplicationUsers(String applicationId, Pageable pageable);

    /**
     * Searches the users of application {@code applicationId} by free text over email and display name. Blank
     * {@code searchText} is equivalent to {@link #findApplicationUsers}.
     */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_MEMBERS)
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
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_MEMBERS)
    Future<OrganizationParticipantIdentity> addOrganizationUser(String email, String displayName, boolean sendInvitation);

    /**
     * Adds a person to one of the caller's organization's applications by email: a user of the application with no
     * sign-in method yet, listed as pending. The person's first sign-in at their login, their tenant's or the
     * application's, through one of its providers under the same address claims the user. With an invitation the
     * person also claims it with a password, or signs in at the application's primary UI. Fails for an address that
     * already has a user in the application, for a tenant missing or one the application does not have, and when the
     * person would have no way to claim the user: without an invitation, no provider at their login; with one, no
     * password there and no provider of the application's own at a primary UI.
     *
     * @param request the person and the application, and whether to email an invitation
     * @return the user as listed, pending until they first sign in
     */
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_MEMBERS)
    Future<ApplicationParticipantIdentity> addApplicationUser(AddApplicationUserRequest request);

    /**
     * Emails an invitation to a pending member of the caller's organization, or to a pending user of its application
     * {@code applicationId} when that is set: the one they have, again, or a new one for someone added without.
     * Fails for an address that is not a pending member's or user's.
     *
     * @param email         the pending member's or user's address
     * @param applicationId the application whose user is invited, or {@code null} for a member of the organization
     */
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_MEMBERS)
    Future<Void> sendInvitation(String email, String applicationId);

    /**
     * Enables or disables a member of the caller's organization or of one of its applications: a user of the
     * organization, or an application user. Disabling gates future logins to that scope;
     * established sessions last until they expire. Callers cannot disable themselves, nor the organization's last
     * enabled user holding {@code organization.admin} on it.
     *
     * @param identityId the member's id: an organization user's or an application user's
     */
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_MEMBERS, consistent = true)
    Future<Void> setMemberEnabled(String identityId, boolean enabled);

    /**
     * Removes a member: a user of the caller's organization or an application user, each with its stored credentials
     * and the invitation it never accepted. Callers cannot remove themselves, nor the organization's last enabled user
     * holding {@code organization.admin} on it.
     *
     * @param identityId the member's id: an organization user's or an application user's
     */
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_MEMBERS, consistent = true)
    Future<Void> removeMember(String identityId);

    /**
     * Lists the scope's live (unexpired) pending invitations: for the organization, those of pending members sent one;
     * for an application, those of its pending users sent one.
     */
    @AuthzCheck(permission = AuthzConstants.CAN_READ_MEMBERS)
    Future<Page<PendingInviteSummary>> findPendingInvites(String applicationId, Pageable pageable);

    /** Cancels a pending invitation belonging to the caller's organization. */
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_MEMBERS)
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
    @AuthzCheck(permission = AuthzConstants.CAN_MANAGE_MEMBERS)
    Future<String> findInvitationLink(String email, String applicationId);
}
