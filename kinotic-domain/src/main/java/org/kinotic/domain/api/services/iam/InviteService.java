package org.kinotic.domain.api.services.iam;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.PendingInvite;

import java.util.concurrent.CompletableFuture;

/**
 * Drives member invitations through one short-lived {@link PendingInvite} record. Two phases:
 * <em>create</em> stashes the record for an <em>existing</em> scope and emails an acceptance link;
 * <em>accept</em> consumes the record and creates the {@link IamUser} at that scope — either with a
 * password (LOCAL) or from a verified OIDC identity (OIDC).
 * <p>
 * This service is organization/application agnostic: the scope lives on each {@link PendingInvite}.
 * It never resolves OIDC configuration itself — the caller supplies the {@code oidcConfigId} on the
 * record — so it is reusable for both organization-scope and application-scope invitations.
 */
public interface InviteService {

    /**
     * Stores an invitation for an existing scope and emails an acceptance link. The caller supplies
     * {@code email}, {@code displayName}, {@code authType}, {@code organizationId}, optional
     * {@code applicationId}, {@code invitedBy}, and — for an OIDC invite — {@code oidcConfigId}; this
     * method sets {@code id}, {@code verificationToken}, {@code created}, and {@code expiresAt}.
     * Fails if a member already exists at that scope for the email, or an invitation is already pending.
     *
     * @return the stored invitation with its generated fields populated
     */
    CompletableFuture<PendingInvite> createInvite(PendingInvite invite);

    /**
     * Returns the invitation for {@code token}, confirming it has not expired. Deletes the record and
     * fails if it has expired; fails if no record matches.
     */
    CompletableFuture<PendingInvite> getValidInvite(String token);

    /**
     * Accepts a LOCAL invitation: validates the token, creates the member at the invitation's scope
     * with the given password, then deletes the invitation.
     *
     * @return the created member
     */
    CompletableFuture<IamUser> acceptLocalInvite(String token, String password);

    /**
     * Accepts an OIDC invitation: validates the token, asserts the invitation is OIDC for
     * {@code oidcConfigId} and that {@code verifiedEmail} matches the invited email, creates the
     * member at the invitation's scope, then deletes the invitation. No credential is stored.
     *
     * @return the created member
     */
    CompletableFuture<IamUser> acceptOidcInvite(String token, String oidcSubject, String oidcConfigId, String verifiedEmail);

    /** Lists pending invitations at the scope identified by {@code (organizationId, applicationId)}. */
    CompletableFuture<Page<PendingInvite>> findPendingInvites(String organizationId, String applicationId, Pageable pageable);

    /**
     * Cancels a pending invitation. Fails if no invitation with {@code inviteId} exists in
     * {@code organizationId} — the org guard prevents cancelling another organization's invitation.
     */
    CompletableFuture<Void> cancelInvite(String inviteId, String organizationId);
}
