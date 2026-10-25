package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.exceptions.ExistingUserException;
import org.kinotic.domain.api.exceptions.InviteEmailMismatchException;
import org.kinotic.domain.api.model.EmailTemplate;
import org.kinotic.domain.api.model.security.PendingInvite;

/**
 * Manages member invitations into an existing Organization or Application scope: creating and
 * emailing the invite, and consuming it when the invitee accepts with a password or a verified
 * OIDC identity.
 */
public interface InviteService {

    /**
     * Creates an invitation and emails its accept link to the invitee. The invite's scope comes
     * from {@code organizationId} (required) and {@code applicationId} (null for an org-member
     * invite). Fails if the email is already a member of the target scope — for org invites an
     * organization user holding a membership of the organization that a sign-in method signs in, for
     * app invites a user of that application — or if an invitation is already pending for it in the
     * scope.
     *
     * @param invite carrying email, optional displayName, scope, and inviter attribution;
     *               id, token, and expiry are assigned here
     * @param emailTemplate the invitation email to send in place of Kinotic's, or {@code null} for Kinotic's
     * @return a future emitting the saved invitation
     * @throws IllegalArgumentException when {@code emailTemplate} is not valid, as {@link EmailTemplate} describes
     */
    Future<PendingInvite> createInvite(PendingInvite invite, EmailTemplate emailTemplate);

    /**
     * Finds the invitation for the given accept token, failing if the token is unknown or the
     * invitation has expired. Used to show invitation details on the accept page.
     */
    Future<PendingInvite> getValidInvite(String token);

    /**
     * Accepts an invitation with a password and consumes it. An invitation into an organization makes the
     * organization user with the invited email a member: when none exists, it is created with {@code password};
     * when one exists, {@code password} must be its password, which proves the acceptor owns it, and a mismatch
     * fails with {@link ExistingUserException} without consuming the invitation. An invitation into an
     * application creates the application's user with {@code password}.
     *
     * @param token       the accept token from the invitation email
     * @param password    the password chosen by a new member, or the existing organization user's
     * @param displayName optional display name of a new member; the invite's value is used when blank
     * @return the new member as a participant of the invitation's scope, for the session to sign in
     */
    Future<Participant> acceptLocalInvite(String token, String password, String displayName);

    /**
     * Accepts an invitation into an organization with a verified OIDC identity and consumes it. The
     * provider-verified email must match the invited email (case-insensitive) or the accept fails with
     * {@link InviteEmailMismatchException}. The organization user the identity is bound to becomes a member, when
     * its email is the invited one; with no bound organization user, the one with the invited email must not exist
     * either, unless an administrator added it and nobody signs in as it yet, which the identity then claims, else
     * the accept fails with {@link ExistingUserException}; with neither, a new organization user bound to the
     * identity is created. An invitation into an application is accepted with {@link #acceptLocalInvite}.
     *
     * @param token         the accept token recovered from the OIDC flow session
     * @param oidcSubject   the {@code sub} claim from the IdP
     * @param oidcConfigId  id of the OIDC configuration that produced the identity
     * @param verifiedEmail the verified {@code email} claim from the IdP
     * @return the new member as a participant of the invitation's scope, for the session to sign in
     */
    Future<Participant> acceptOidcInvite(String token,
                                         String oidcSubject,
                                         String oidcConfigId,
                                         String verifiedEmail);

    /**
     * Lists the live (unexpired) invitations for the given scope. {@code applicationId} null
     * lists org-member invites; set lists that application's invites.
     */
    Future<Page<PendingInvite>> findPendingInvites(String organizationId,
                                                   String applicationId,
                                                   Pageable pageable);

    /**
     * Cancels (deletes) a pending invitation. Fails unless the invitation belongs to the given
     * organization.
     *
     * @param inviteId       id of the invitation to cancel
     * @param organizationId the organization the caller is acting for
     */
    Future<Void> cancelInvite(String inviteId, String organizationId);

    /** The live invitation of {@code email} into organization {@code organizationId}, or {@code null} when none is pending. */
    Future<PendingInvite> findPendingInvite(String email, String organizationId);

    /**
     * The accept link of the live invitation of {@code email} into the scope, for the inviter to hand over when no
     * invitation email reaches the invitee: {@code kinotic.domain.email.enabled} is {@code false}. Anyone holding the
     * link can accept the invitation as the invited address.
     *
     * @param email          the invited address
     * @param organizationId the organization of the scope
     * @param applicationId  the application of the scope, or {@code null} for an invitation into the organization
     * @return a future emitting the link, or {@code null} when links are not shown in the UI or no invitation is pending
     */
    Future<String> findAcceptUrl(String email, String organizationId, String applicationId);

    /**
     * Emails the invitation {@code inviteId} of organization {@code organizationId} again, to the same address
     * with the same accept token, as the invitation email {@code emailTemplate}, or Kinotic's when it is {@code null}.
     * Fails when the organization has no such invitation.
     *
     * @throws IllegalArgumentException when {@code emailTemplate} is not valid, as {@link EmailTemplate} describes
     */
    Future<Void> resendInvite(String inviteId, String organizationId, EmailTemplate emailTemplate);
}
