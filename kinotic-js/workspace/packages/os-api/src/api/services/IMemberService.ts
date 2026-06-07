import {
    FunctionalIterablePage,
    type IDataSource,
    type IKinotic,
    type IServiceProxy,
    type IterablePage,
    type Page,
    type Pageable
} from '@kinotic-ai/core'
import { IamUser } from '@/api/model/iam/IamUser'
import { AuthType } from '@/api/model/iam/AuthType'
import type { InviteOptions } from '@/api/model/iam/InviteOptions'
import { PendingInvite } from '@/api/model/iam/PendingInvite'

/**
 * Client proxy for the published {@code MemberService}. Manages the members of a scope within the
 * signed-in administrator's organization; the organization is sourced server-side from the bound
 * participant. {@code applicationId} selects the scope — {@code null} for organization members, a
 * non-null id for that application's members.
 */
export interface IMemberService {

    /** Lists the members of the given scope (organization members when {@code applicationId} is null). */
    findMembers(applicationId: string | null, pageable: Pageable): Promise<IterablePage<IamUser>>

    /** Full-text search over the members of the given scope by email/display name. */
    searchMembers(applicationId: string | null, searchText: string, pageable: Pageable): Promise<IterablePage<IamUser>>

    /** Reports which authentication methods may be offered when inviting a member into the given scope. */
    inviteOptions(applicationId: string | null): Promise<InviteOptions>

    /** Invites a member into the given scope by email. */
    inviteMember(applicationId: string | null, email: string, displayName: string | null, authType: AuthType): Promise<PendingInvite>

    /** Enables or disables a member. Rejects acting on the caller's own account. */
    setMemberEnabled(id: string, enabled: boolean): Promise<IamUser>

    /** Removes a member (and any password credential). Rejects acting on the caller's own account. */
    removeMember(id: string): Promise<void>

    /** Lists the pending invitations for the given scope. */
    findPendingInvites(applicationId: string | null, pageable: Pageable): Promise<IterablePage<PendingInvite>>

    /** Cancels a pending invitation in the administrator's organization. */
    cancelInvite(inviteId: string): Promise<void>

    /**
     * A scope-bound {@link IDataSource} over the members of {@code applicationId}, suitable for a
     * {@code CrudTable}: its {@code findAll}/{@code search} delegate to {@link findMembers}/{@link searchMembers}.
     */
    memberDataSource(applicationId: string | null): IDataSource<IamUser>
}

export class MemberService implements IMemberService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy('org.kinotic.os.api.services.iam.MemberService')
    }

    public async findMembers(applicationId: string | null, pageable: Pageable): Promise<IterablePage<IamUser>> {
        const page: Page<IamUser> = await this.findMembersSinglePage(applicationId, pageable)
        return new FunctionalIterablePage(pageable, page,
            (next: Pageable) => this.findMembersSinglePage(applicationId, next))
    }

    public async searchMembers(applicationId: string | null, searchText: string, pageable: Pageable): Promise<IterablePage<IamUser>> {
        const page: Page<IamUser> = await this.searchMembersSinglePage(applicationId, searchText, pageable)
        return new FunctionalIterablePage(pageable, page,
            (next: Pageable) => this.searchMembersSinglePage(applicationId, searchText, next))
    }

    public inviteOptions(applicationId: string | null): Promise<InviteOptions> {
        return this.serviceProxy.invoke('inviteOptions', [applicationId])
    }

    public inviteMember(applicationId: string | null, email: string, displayName: string | null, authType: AuthType): Promise<PendingInvite> {
        return this.serviceProxy.invoke('inviteMember', [applicationId, email, displayName, authType])
    }

    public setMemberEnabled(id: string, enabled: boolean): Promise<IamUser> {
        return this.serviceProxy.invoke('setMemberEnabled', [id, enabled])
    }

    public removeMember(id: string): Promise<void> {
        return this.serviceProxy.invoke('removeMember', [id])
    }

    public async findPendingInvites(applicationId: string | null, pageable: Pageable): Promise<IterablePage<PendingInvite>> {
        const page: Page<PendingInvite> = await this.findPendingInvitesSinglePage(applicationId, pageable)
        return new FunctionalIterablePage(pageable, page,
            (next: Pageable) => this.findPendingInvitesSinglePage(applicationId, next))
    }

    public cancelInvite(inviteId: string): Promise<void> {
        return this.serviceProxy.invoke('cancelInvite', [inviteId])
    }

    public memberDataSource(applicationId: string | null): IDataSource<IamUser> {
        return {
            findAll: (pageable: Pageable) => this.findMembers(applicationId, pageable),
            search: (searchText: string, pageable: Pageable) => this.searchMembers(applicationId, searchText, pageable)
        }
    }

    private findMembersSinglePage(applicationId: string | null, pageable: Pageable): Promise<Page<IamUser>> {
        return this.serviceProxy.invoke('findMembers', [applicationId, pageable])
    }

    private searchMembersSinglePage(applicationId: string | null, searchText: string, pageable: Pageable): Promise<Page<IamUser>> {
        return this.serviceProxy.invoke('searchMembers', [applicationId, searchText, pageable])
    }

    private findPendingInvitesSinglePage(applicationId: string | null, pageable: Pageable): Promise<Page<PendingInvite>> {
        return this.serviceProxy.invoke('findPendingInvites', [applicationId, pageable])
    }
}
