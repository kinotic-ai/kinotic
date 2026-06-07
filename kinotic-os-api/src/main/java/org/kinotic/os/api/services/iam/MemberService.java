package org.kinotic.os.api.services.iam;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.iam.AuthType;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.PendingInvite;

import java.util.concurrent.CompletableFuture;

/**
 * Browser-invoked facade for managing the members of a scope within the signed-in administrator's
 * organization. The organization is always sourced from the bound
 * {@link org.kinotic.domain.api.security.OrganizationParticipant}; sessions bound to an
 * application participant are rejected. The {@code applicationId} argument selects the scope:
 * {@code null} addresses organization members, a non-null id addresses the members of that
 * application (which must belong to the administrator's organization).
 * <p>
 * Every query is pinned to the participant's organization, so a foreign {@code applicationId}
 * returns no rows rather than leaking another organization's data.
 */
@Publish
public interface MemberService {

    /** Lists the members of the given scope (organization members when {@code applicationId} is null). */
    CompletableFuture<Page<IamUser>> findMembers(String applicationId, Pageable pageable);

    /** Full-text search over the members of the given scope by email/display name. */
    CompletableFuture<Page<IamUser>> searchMembers(String applicationId, String searchText, Pageable pageable);

    /**
     * Reports which authentication methods may be offered when inviting a member into the given
     * scope — LOCAL always, OIDC when the scope has an enabled provider.
     */
    CompletableFuture<InviteOptions> inviteOptions(String applicationId);

    /**
     * Invites a member into the given scope by email. The organization is taken from the bound
     * participant; for an OIDC invite the scope's OIDC configuration is resolved server-side. Fails
     * if a member already exists at that scope for the email, or an invitation is already pending.
     *
     * @return the created pending invitation
     */
    CompletableFuture<PendingInvite> inviteMember(String applicationId, String email, String displayName, AuthType authType);

    /**
     * Enables or disables a member. Fails if the member is not in the administrator's organization,
     * or if the member id is the caller's own (an administrator cannot lock themselves out).
     *
     * @return the updated member
     */
    CompletableFuture<IamUser> setMemberEnabled(String id, boolean enabled);

    /**
     * Removes a member (and any password credential). Fails if the member is not in the
     * administrator's organization, or if the member id is the caller's own.
     */
    CompletableFuture<Void> removeMember(String id);

    /** Lists the pending invitations for the given scope. */
    CompletableFuture<Page<PendingInvite>> findPendingInvites(String applicationId, Pageable pageable);

    /** Cancels a pending invitation in the administrator's organization. */
    CompletableFuture<Void> cancelInvite(String inviteId);
}
