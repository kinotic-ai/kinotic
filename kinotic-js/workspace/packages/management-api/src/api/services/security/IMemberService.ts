import { MANAGEMENT_API_ZONE } from '@/api/Zones'
import {
    FunctionalIterablePage,
    type IDataSource,
    type IKinotic,
    type IServiceProxy,
    type IterablePage,
    type Page,
    type Pageable
} from '@kinotic-ai/core'
import { OrganizationMember } from '@/api/model/security/OrganizationMember'
import type { ApplicationParticipantIdentity } from '@/api/model/security/ApplicationParticipantIdentity'
import { PendingInviteSummary } from '@/api/model/security/PendingInviteSummary'

/**
 * Member management for the caller's organization and its applications. The organization's members are
 * {@link OrganizationMember}s and an application's members are its {@link ApplicationParticipantIdentity}s.
 * Invitations are scoped by applicationId — null addresses the organization's, set addresses that application's —
 * and the backend derives the organization from the authenticated participant, so it is never a parameter.
 */
export interface IMemberService {

    /** Lists the organization's members. */
    findMembers(pageable: Pageable): Promise<IterablePage<OrganizationMember>>

    /**
     * Searches the organization's members by free text over email and display name. Blank
     * searchText is equivalent to {@link findMembers}.
     */
    searchMembers(searchText: string, pageable: Pageable): Promise<IterablePage<OrganizationMember>>

    /** Lists the users of one of the organization's applications. */
    findApplicationUsers(applicationId: string, pageable: Pageable): Promise<IterablePage<ApplicationParticipantIdentity>>

    /**
     * Searches the users of one of the organization's applications by free text over email and display name. Blank
     * searchText is equivalent to {@link findApplicationUsers}.
     */
    searchApplicationUsers(searchText: string, applicationId: string, pageable: Pageable): Promise<IterablePage<ApplicationParticipantIdentity>>

    /**
     * Adds a person to the organization by email: a user of the organization with no sign-in method yet, listed as
     * pending. With sendInvitation an invitation is emailed, whose acceptance claims the member with a password or
     * through one of the organization's providers; without one the person signs in through one of the organization's
     * own providers, which claims the member on the first sign-in, so the organization must have one. Rejects an
     * address that is already a member, or with no invitation sent when the organization has no provider of its own.
     * Returns the member as listed, pending until they first sign in.
     */
    addMember(email: string, displayName: string | null, sendInvitation: boolean): Promise<OrganizationMember>

    /**
     * Emails an invitation to a pending member of the organization: the one they have, again, or a new one for a
     * member added without. Rejects an address that is not a pending member's.
     */
    sendInvitation(email: string): Promise<void>

    /**
     * Invites someone into one of the organization's applications by email. Sends the invitation email and returns
     * the pending invitation, whose acceptance creates the application's user. Rejects when the email already has a
     * user in the application or an invitation is already pending for it.
     */
    inviteMember(email: string, displayName: string | null, applicationId: string): Promise<PendingInviteSummary>

    /**
     * Enables or disables a member of the caller's organization or a user of one of its applications. Disabling gates future
     * logins; established sessions last until they expire. Callers cannot disable themselves.
     */
    setMemberEnabled(userId: string, enabled: boolean): Promise<void>

    /**
     * Removes a member of the caller's organization, along with the invitation it never accepted, or an application
     * user, each with its stored credentials. Callers cannot remove themselves.
     */
    removeMember(userId: string): Promise<void>

    /** Lists the scope's live (unexpired) pending invitations. */
    findPendingInvites(applicationId: string | null, pageable: Pageable): Promise<IterablePage<PendingInviteSummary>>

    /** Cancels a pending invitation belonging to the caller's organization. */
    cancelInvite(inviteId: string): Promise<void>

    /** An {@link IDataSource} over the organization's members, for table components. */
    memberDataSource(): IDataSource<OrganizationMember>

}

export class MemberService implements IMemberService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.security.MemberService`)
    }

    public async findMembers(pageable: Pageable): Promise<IterablePage<OrganizationMember>> {
        const page: Page<OrganizationMember> = await this.serviceProxy.invoke('findMembers', [pageable])
        return new FunctionalIterablePage(pageable, page,
            (next: Pageable) => this.serviceProxy.invoke('findMembers', [next]))
    }

    public async searchMembers(searchText: string, pageable: Pageable): Promise<IterablePage<OrganizationMember>> {
        const page: Page<OrganizationMember> = await this.serviceProxy.invoke('searchMembers', [searchText, pageable])
        return new FunctionalIterablePage(pageable, page,
            (next: Pageable) => this.serviceProxy.invoke('searchMembers', [searchText, next]))
    }

    public async findApplicationUsers(applicationId: string, pageable: Pageable): Promise<IterablePage<ApplicationParticipantIdentity>> {
        const page: Page<ApplicationParticipantIdentity> = await this.serviceProxy.invoke('findApplicationUsers', [applicationId, pageable])
        return new FunctionalIterablePage(pageable, page,
            (next: Pageable) => this.serviceProxy.invoke('findApplicationUsers', [applicationId, next]))
    }

    public async searchApplicationUsers(searchText: string, applicationId: string, pageable: Pageable): Promise<IterablePage<ApplicationParticipantIdentity>> {
        const page: Page<ApplicationParticipantIdentity> = await this.serviceProxy.invoke('searchApplicationUsers', [searchText, applicationId, pageable])
        return new FunctionalIterablePage(pageable, page,
            (next: Pageable) => this.serviceProxy.invoke('searchApplicationUsers', [searchText, applicationId, next]))
    }

    public addMember(email: string, displayName: string | null, sendInvitation: boolean): Promise<OrganizationMember> {
        return this.serviceProxy.invoke('addMember', [email, displayName, sendInvitation])
    }

    public sendInvitation(email: string): Promise<void> {
        return this.serviceProxy.invoke('sendInvitation', [email])
    }

    public inviteMember(email: string, displayName: string | null, applicationId: string): Promise<PendingInviteSummary> {
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

    public memberDataSource(): IDataSource<OrganizationMember> {
        return {
            findAll: (pageable: Pageable) => this.findMembers(pageable),
            search: (searchText: string, pageable: Pageable) => this.searchMembers(searchText, pageable)
        }
    }
}
