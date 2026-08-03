package org.kinotic.domain.api.services.iam;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.iam.ParticipantIdentity;
import org.kinotic.domain.api.model.iam.PendingInvite;

import java.util.concurrent.CompletableFuture;

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
    CompletableFuture<PendingInvite> createInvite(PendingInvite invite);

    /**
     * Finds the invitation for the given accept token, failing if the token is unknown or the
     * invitation has expired. Used to show invitation details on the accept page.
     */
    CompletableFuture<PendingInvite> getValidInvite(String token);

    /**
     * Accepts an invitation by setting a password. Creates the member in the invite's stored
     * scope and consumes the invitation.
     *
     * @param token       the accept token from the invitation email
     * @param password    the password chosen by the invitee
     * @param displayName optional display-name override; the invite's value is used when blank
     * @return a future emitting the created member
     */
    CompletableFuture<ParticipantIdentity> acceptLocalInvite(String token, String password, String displayName);

    /**
     * Accepts an invitation with a verified OIDC identity. The IdP-verified email must match the
     * invited email (case-insensitive) or the accept fails. Creates the member in the invite's
     * stored scope, carrying the OIDC identity, and consumes the invitation.
     *
     * @param token         the accept token recovered from the OIDC flow session
     * @param oidcSubject   the {@code sub} claim from the IdP
     * @param oidcConfigId  id of the OIDC configuration that produced the identity
     * @param verifiedEmail the verified {@code email} claim from the IdP
     * @return a future emitting the created member
     */
    CompletableFuture<ParticipantIdentity> acceptOidcInvite(String token,
                                                String oidcSubject,
                                                String oidcConfigId,
                                                String verifiedEmail);

    /**
     * Lists the live (unexpired) invitations for the given scope. {@code applicationId} null
     * lists org-member invites; set lists that application's invites.
     */
    CompletableFuture<Page<PendingInvite>> findPendingInvites(String organizationId,
                                                              String applicationId,
                                                              Pageable pageable);

    /**
     * Cancels (deletes) a pending invitation. Fails unless the invitation belongs to the given
     * organization.
     *
     * @param inviteId       id of the invitation to cancel
     * @param organizationId the organization the caller is acting for
     */
    CompletableFuture<Void> cancelInvite(String inviteId, String organizationId);
}
