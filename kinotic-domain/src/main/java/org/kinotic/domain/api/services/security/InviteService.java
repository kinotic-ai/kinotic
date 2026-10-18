package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.exceptions.ExistingAccountException;
import org.kinotic.domain.api.exceptions.InviteEmailMismatchException;
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
     * invite). Fails if the email already has an account in the target scope — for org invites
     * an org-scope account anywhere, for app invites an account in that application — or if an
     * invitation is already pending for it in the scope.
     *
     * @param invite carrying email, optional displayName, scope, and inviter attribution;
     *               id, token, and expiry are assigned here
     * @return a future emitting the saved invitation
     */
    Future<PendingInvite> createInvite(PendingInvite invite);

    /**
     * Finds the invitation for the given accept token, failing if the token is unknown or the
     * invitation has expired. Used to show invitation details on the accept page.
     */
    Future<PendingInvite> getValidInvite(String token);

    /**
     * Accepts an invitation with a password and consumes it. An invitation into an organization makes the
     * account with the invited email a member: when none exists, the account is created with
     * {@code password}; when one exists, {@code password} must be that account's, which proves the
     * acceptor owns it, and a mismatch fails with {@link ExistingAccountException} without consuming the
     * invitation. An invitation into an application creates the application's user with {@code password}.
     *
     * @param token       the accept token from the invitation email
     * @param password    the password chosen by a new member, or the existing account's
     * @param displayName optional display name of a new member; the invite's value is used when blank
     * @return the new member as a participant of the invitation's scope, for the session to sign in
     */
    Future<Participant> acceptLocalInvite(String token, String password, String displayName);

    /**
     * Accepts an invitation with a verified OIDC identity and consumes it. The provider-verified email must
     * match the invited email (case-insensitive) or the accept fails with
     * {@link InviteEmailMismatchException}. An invitation into an organization makes the account the
     * identity is bound to a member, when its email is the invited one; with no bound account, the account
     * with the invited email must not exist either, else the accept fails with
     * {@link ExistingAccountException}, and a new account bound to the identity is created. An invitation
     * into an application creates the application's user bound to the identity.
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
}
