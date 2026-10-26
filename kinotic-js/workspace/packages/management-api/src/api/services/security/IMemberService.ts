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
import type { ApplicationParticipantIdentity } from '@/api/model/security/ApplicationParticipantIdentity'
import type { OrganizationParticipantIdentity } from '@/api/model/security/OrganizationParticipantIdentity'
import { PendingInviteSummary } from '@/api/model/security/PendingInviteSummary'
import type { AddApplicationUserRequest } from '@/api/model/AddApplicationUserRequest'

/**
 * Member management for the caller's organization and its applications. The organization's members are its
 * {@link OrganizationParticipantIdentity}s and an application's members are its {@link ApplicationParticipantIdentity}s,
 * each pending until it first signs in.
 * Invitations are scoped by applicationId — null addresses the organization's, set addresses that application's —
 * and the backend derives the organization from the authenticated participant, so it is never a parameter.
 */
export interface IMemberService {

    /** Lists the organization's members. */
    findMembers(pageable: Pageable): Promise<IterablePage<OrganizationParticipantIdentity>>

    /**
     * Searches the organization's members by free text over email and display name. Blank
     * searchText is equivalent to {@link findMembers}.
     */
    searchMembers(searchText: string, pageable: Pageable): Promise<IterablePage<OrganizationParticipantIdentity>>

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
    addOrganizationUser(email: string, displayName: string | null, sendInvitation: boolean): Promise<OrganizationParticipantIdentity>

    /**
     * Adds a person to one of the organization's applications by email: a user of the application with no sign-in
     * method yet, listed as pending. The person's first sign-in at their login, their tenant's or the application's,
     * through one of its providers under the same address claims the user. With an invitation the person also claims
     * it with a password, or signs in at the application's primary UI. Rejects an address that already has a user in
     * the application, a tenant missing or one the application does not have, and a person who would have no way to
     * claim the user: without an invitation, no provider at their login; with one, no password there and no provider
     * of the application's own at a primary UI. Returns the user as listed, pending until they first sign in.
     */
    addApplicationUser(request: AddApplicationUserRequest): Promise<ApplicationParticipantIdentity>

    /**
     * Emails an invitation to a pending member of the organization, or to a pending user of its application
     * applicationId when that is set: the one they have, again, or a new one for someone added without. Rejects an
     * address that is not a pending member's or user's.
     */
    sendInvitation(email: string, applicationId: string | null): Promise<void>

    /**
     * Enables or disables a member of the caller's organization or a user of one of its applications. Disabling gates future
     * logins; established sessions last until they expire. Callers cannot disable themselves.
     */
    setMemberEnabled(userId: string, enabled: boolean): Promise<void>

    /**
     * Removes a member of the caller's organization or an application user, each with its stored credentials and the
     * invitation it never accepted. Callers cannot remove themselves.
     */
    removeMember(userId: string): Promise<void>

    /** Lists the scope's live (unexpired) pending invitations. */
    findPendingInvites(applicationId: string | null, pageable: Pageable): Promise<IterablePage<PendingInviteSummary>>

    /** Cancels a pending invitation belonging to the caller's organization. */
    cancelInvite(inviteId: string): Promise<void>

    /**
     * The accept link of the live invitation of email into the caller's organization (applicationId null) or one of
     * its applications, for the inviter to send on when email is disabled, as on a demonstration server. Anyone holding
     * the link can accept the invitation as the invited address. Resolves null when email is enabled or no invitation
     * into the scope is pending for the address.
     */
    findInvitationLink(email: string, applicationId: string | null): Promise<string | null>

    /** An {@link IDataSource} over the organization's members, for table components. */
    memberDataSource(): IDataSource<OrganizationParticipantIdentity>

}

export class MemberService implements IMemberService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.security.MemberService`)
    }

    public async findMembers(pageable: Pageable): Promise<IterablePage<OrganizationParticipantIdentity>> {
        const page: Page<OrganizationParticipantIdentity> = await this.serviceProxy.invoke('findMembers', [pageable])
        return new FunctionalIterablePage(pageable, page,
            (next: Pageable) => this.serviceProxy.invoke('findMembers', [next]))
    }

    public async searchMembers(searchText: string, pageable: Pageable): Promise<IterablePage<OrganizationParticipantIdentity>> {
        const page: Page<OrganizationParticipantIdentity> = await this.serviceProxy.invoke('searchMembers', [searchText, pageable])
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

    public addOrganizationUser(email: string, displayName: string | null, sendInvitation: boolean): Promise<OrganizationParticipantIdentity> {
        return this.serviceProxy.invoke('addOrganizationUser', [email, displayName, sendInvitation])
    }

    public addApplicationUser(request: AddApplicationUserRequest): Promise<ApplicationParticipantIdentity> {
        return this.serviceProxy.invoke('addApplicationUser', [request])
    }

    public sendInvitation(email: string, applicationId: string | null): Promise<void> {
        return this.serviceProxy.invoke('sendInvitation', [email, applicationId])
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

    public findInvitationLink(email: string, applicationId: string | null): Promise<string | null> {
        return this.serviceProxy.invoke('findInvitationLink', [email, applicationId])
    }

    public memberDataSource(): IDataSource<OrganizationParticipantIdentity> {
        return {
            findAll: (pageable: Pageable) => this.findMembers(pageable),
            search: (searchText: string, pageable: Pageable) => this.searchMembers(searchText, pageable)
        }
    }
}
