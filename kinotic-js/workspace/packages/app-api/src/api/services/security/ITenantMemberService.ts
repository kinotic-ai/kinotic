import { APP_API_ZONE,
         type Grant,
         type PendingInviteSummary,
         type RoleDefinition,
         type Subject,
         type UserParticipantIdentity } from '@kinotic-ai/management-api'
import {
    FunctionalIterablePage,
    type IKinotic,
    type IServiceProxy,
    type IterablePage,
    type Page,
    type Pageable
} from '@kinotic-ai/core'

/**
 * The users of the connected user's tenant, for an application's own pages: who belongs to it, the invitations
 * into it, and the grants its users hold on it. Every function acts on the caller's tenant, and what the caller
 * may do is checked on that tenant in the application's store: viewing its members needs
 * tenant_can_view_members, managing them tenant_can_manage_members, viewing its grants tenant_can_view_access
 * and managing them tenant_can_manage_access, all of which the tenant's administrator holds.
 */
export interface ITenantMemberService {

    /** The users of the tenant. */
    findMembers(pageable: Pageable): Promise<IterablePage<UserParticipantIdentity>>

    /**
     * Invites someone into the tenant by email: sends the invitation email and returns the pending invitation.
     * Rejects when the email already has an account in the application or an invitation is pending for it,
     * and when the application does not enable invitations into a tenant.
     */
    inviteMember(email: string, displayName: string | null): Promise<PendingInviteSummary>

    /** The live (unexpired) invitations into the tenant. */
    findPendingInvites(pageable: Pageable): Promise<IterablePage<PendingInviteSummary>>

    /** Cancels an invitation into the tenant. */
    cancelInvite(inviteId: string): Promise<void>

    /**
     * Permanently removes a user of the tenant, including any stored credential. Callers cannot remove
     * themselves.
     */
    removeMember(userId: string): Promise<void>

    /**
     * Every role a grant on the tenant can name: the built-in roles of the tenant and of the rows of each entity
     * definition inside it, each with the permissions it bundles.
     */
    findRoles(): Promise<RoleDefinition[]>

    /** The grants made on the tenant. */
    findGrants(): Promise<Grant[]>

    /**
     * Grants a role to a user of the tenant on the tenant, so the user holds every permission the role bundles
     * on the tenant and on every row inside it.
     * @param subject a user naming the identity id of one of the tenant's users
     * @param roleId the role granted, one {@link findRoles} lists, such as tenant.viewer or invoice.editor
     */
    grant(subject: Subject, roleId: string): Promise<Grant>

    /** Revokes a grant made on the tenant. */
    revoke(grantId: string): Promise<void>

}

export class TenantMemberService implements ITenantMemberService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${APP_API_ZONE}~org.kinotic.app.api.services.security.TenantMemberService`)
    }

    public async findMembers(pageable: Pageable): Promise<IterablePage<UserParticipantIdentity>> {
        const page: Page<UserParticipantIdentity> = await this.serviceProxy.invoke('findMembers', [pageable])
        return new FunctionalIterablePage(pageable, page,
            (next: Pageable) => this.serviceProxy.invoke('findMembers', [next]))
    }

    public inviteMember(email: string, displayName: string | null): Promise<PendingInviteSummary> {
        return this.serviceProxy.invoke('inviteMember', [email, displayName])
    }

    public async findPendingInvites(pageable: Pageable): Promise<IterablePage<PendingInviteSummary>> {
        const page: Page<PendingInviteSummary> = await this.serviceProxy.invoke('findPendingInvites', [pageable])
        return new FunctionalIterablePage(pageable, page,
            (next: Pageable) => this.serviceProxy.invoke('findPendingInvites', [next]))
    }

    public cancelInvite(inviteId: string): Promise<void> {
        return this.serviceProxy.invoke('cancelInvite', [inviteId])
    }

    public removeMember(userId: string): Promise<void> {
        return this.serviceProxy.invoke('removeMember', [userId])
    }

    public findRoles(): Promise<RoleDefinition[]> {
        return this.serviceProxy.invoke('findRoles', [])
    }

    public findGrants(): Promise<Grant[]> {
        return this.serviceProxy.invoke('findGrants', [])
    }

    public grant(subject: Subject, roleId: string): Promise<Grant> {
        return this.serviceProxy.invoke('grant', [subject, roleId])
    }

    public revoke(grantId: string): Promise<void> {
        return this.serviceProxy.invoke('revoke', [grantId])
    }

}
