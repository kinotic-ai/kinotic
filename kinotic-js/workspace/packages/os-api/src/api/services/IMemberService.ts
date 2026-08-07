import { OS_API_ZONE } from '@/api/PlatformZones'
import {
    FunctionalIterablePage,
    type IDataSource,
    type IKinotic,
    type IServiceProxy,
    type IterablePage,
    type Page,
    type Pageable
} from '@kinotic-ai/core'
import { UserParticipantIdentity } from '@/api/model/security/UserParticipantIdentity'
import { PendingInviteSummary } from '@/api/model/security/PendingInviteSummary'

/**
 * Member management for the caller's organization and its applications. Scope is selected
 * with applicationId — null addresses org members, set addresses that application's
 * members — and the backend derives the organization from the authenticated participant,
 * so it is never a parameter.
 */
export interface IMemberService {

    /** Lists the members of the scope. */
    findMembers(applicationId: string | null, pageable: Pageable): Promise<IterablePage<UserParticipantIdentity>>

    /**
     * Searches the scope's members by free text over email and display name. Blank
     * searchText is equivalent to {@link findMembers}.
     */
    searchMembers(searchText: string, applicationId: string | null, pageable: Pageable): Promise<IterablePage<UserParticipantIdentity>>

    /**
     * Invites someone into the scope by email. Sends the invitation email and returns the
     * pending invitation. Rejects when the email already has an account in the scope or an
     * invitation is already pending for it.
     */
    inviteMember(email: string, displayName: string | null, applicationId: string | null): Promise<PendingInviteSummary>

    /**
     * Enables or disables a member of the caller's organization. Disabling gates future
     * logins; established sessions last until they expire. Callers cannot disable themselves.
     */
    setMemberEnabled(userId: string, enabled: boolean): Promise<void>

    /**
     * Permanently removes a member of the caller's organization, including any stored
     * credential. Callers cannot remove themselves.
     */
    removeMember(userId: string): Promise<void>

    /** Lists the scope's live (unexpired) pending invitations. */
    findPendingInvites(applicationId: string | null, pageable: Pageable): Promise<IterablePage<PendingInviteSummary>>

    /** Cancels a pending invitation belonging to the caller's organization. */
    cancelInvite(inviteId: string): Promise<void>

    /** An {@link IDataSource} over the scope's members, for table components. */
    memberDataSource(applicationId: string | null): IDataSource<UserParticipantIdentity>

}

export class MemberService implements IMemberService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${OS_API_ZONE}~org.kinotic.os.api.services.security.MemberService`)
    }

    public async findMembers(applicationId: string | null, pageable: Pageable): Promise<IterablePage<UserParticipantIdentity>> {
        const page: Page<UserParticipantIdentity> = await this.serviceProxy.invoke('findMembers', [applicationId, pageable])
        return new FunctionalIterablePage(pageable, page,
            (next: Pageable) => this.serviceProxy.invoke('findMembers', [applicationId, next]))
    }

    public async searchMembers(searchText: string, applicationId: string | null, pageable: Pageable): Promise<IterablePage<UserParticipantIdentity>> {
        const page: Page<UserParticipantIdentity> = await this.serviceProxy.invoke('searchMembers', [searchText, applicationId, pageable])
        return new FunctionalIterablePage(pageable, page,
            (next: Pageable) => this.serviceProxy.invoke('searchMembers', [searchText, applicationId, next]))
    }

    public inviteMember(email: string, displayName: string | null, applicationId: string | null): Promise<PendingInviteSummary> {
        return this.serviceProxy.invoke('inviteMember', [email, displayName, applicationId])
    }

    public setMemberEnabled(userId: string, enabled: boolean): Promise<void> {
        return this.serviceProxy.invoke('setMemberEnabled', [userId, enabled])
    }

    public removeMember(userId: string): Promise<void> {
        return this.serviceProxy.invoke('removeMember', [userId])
    }

    public async findPendingInvites(applicationId: string | null, pageable: Pageable): Promise<IterablePage<PendingInviteSummary>> {
        const page: Page<PendingInviteSummary> = await this.serviceProxy.invoke('findPendingInvites', [applicationId, pageable])
        return new FunctionalIterablePage(pageable, page,
            (next: Pageable) => this.serviceProxy.invoke('findPendingInvites', [applicationId, next]))
    }

    public cancelInvite(inviteId: string): Promise<void> {
        return this.serviceProxy.invoke('cancelInvite', [inviteId])
    }

    public memberDataSource(applicationId: string | null): IDataSource<UserParticipantIdentity> {
        return {
            findAll: (pageable: Pageable) => this.findMembers(applicationId, pageable),
            search: (searchText: string, pageable: Pageable) => this.searchMembers(searchText, applicationId, pageable)
        }
    }
}
