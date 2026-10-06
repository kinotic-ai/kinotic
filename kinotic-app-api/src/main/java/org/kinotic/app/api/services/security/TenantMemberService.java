package org.kinotic.app.api.services.security;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.Grant;
import org.kinotic.authz.api.model.RoleDefinition;
import org.kinotic.authz.api.model.Subject;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.annotations.Version;
import org.kinotic.core.api.annotations.Zone;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.OnboardingMechanism;
import org.kinotic.domain.api.model.security.PendingInviteSummary;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzUtil;

import java.util.List;

/**
 * The users of the caller's tenant, for an application's own pages: who belongs to it, the invitations into it,
 * and the grants its users hold on it. Every function acts on the caller's tenant, so only an application
 * participant that belongs to one may call, and what the caller may do is checked on that tenant in the
 * application's store. An invitation is offered only by an application that enables
 * {@link OnboardingMechanism#TENANT_INVITE}.
 */
@Publish
@Version("1.0.0")
@Zone(DomainUtil.APP_API_ZONE)
@AuthzResource(value = AuthzUtil.TENANT_TYPE, parent = AuthzUtil.APPLICATION_TYPE, resourceId = "{@tenantId}")
public interface TenantMemberService {

    /**
     * The users of the tenant.
     */
    @AuthzCheck(permission = "can_view_members")
    Future<Page<UserParticipantIdentity>> findMembers(Pageable pageable);

    /**
     * Invites someone into the tenant by email: sends the invitation email and returns the pending invitation.
     * Fails if the email already has an account in the application or an invitation is pending for it, and
     * when the application does not enable invitations into a tenant.
     *
     * @param email       where to send the invitation
     * @param displayName optional display name for the invitee
     */
    @AuthzCheck(permission = "can_manage_members")
    Future<PendingInviteSummary> inviteMember(String email, String displayName);

    /**
     * The live (unexpired) invitations into the tenant.
     */
    @AuthzCheck(permission = "can_view_members")
    Future<Page<PendingInviteSummary>> findPendingInvites(Pageable pageable);

    /**
     * Cancels an invitation into the tenant.
     */
    @AuthzCheck(permission = "can_manage_members")
    Future<Void> cancelInvite(String inviteId);

    /**
     * Permanently removes a user of the tenant, including any stored credential. Callers cannot remove
     * themselves.
     */
    @AuthzCheck(permission = "can_manage_members", consistent = true)
    Future<Void> removeMember(String identityId);

    /**
     * Every role a grant on the tenant can name: the built-in roles of the tenant and of the rows of each entity
     * definition inside it, each with the permissions it bundles.
     */
    @AuthzCheck(permission = "can_view_access")
    Future<List<RoleDefinition>> findRoles();

    /**
     * The grants made on the tenant.
     */
    @AuthzCheck(permission = "can_view_access")
    Future<List<Grant>> findGrants();

    /**
     * Grants a role to a user of the tenant on the tenant, so the user holds every permission the role bundles
     * on the tenant and on every row inside it.
     *
     * @param subject a user naming the identity id of one of the tenant's users
     * @param roleId  the role granted, one {@link #findRoles} lists
     * @return the grant
     */
    @AuthzCheck(permission = "can_manage_access")
    Future<Grant> grant(Subject subject, String roleId);

    /**
     * Revokes a grant made on the tenant.
     *
     * @param grantId the grant
     */
    @AuthzCheck(permission = "can_manage_access")
    Future<Void> revoke(String grantId);

}
