import { SYSTEM_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic, IServiceProxy, Page, Pageable } from '@kinotic-ai/core'
import { Application } from '@/api/model/Application'
import { Organization } from '@/api/model/Organization'
import { Project } from '@/api/model/Project'
import type { PendingInviteSummary } from '@/api/model/security/PendingInviteSummary'
import type { UserParticipantIdentity } from '@/api/model/security/UserParticipantIdentity'

/**
 * Cross-organization reads for platform operators. Published in the system zone, which only
 * SYSTEM participants may address, so every method takes the target organization explicitly
 * instead of resolving it from the caller's scope.
 */
export interface ISystemOrganizationService {

    findOrganizations(pageable: Pageable): Promise<Page<Organization>>

    searchOrganizations(searchText: string, pageable: Pageable): Promise<Page<Organization>>

    /** Resolves null when no organization has the given id. */
    findOrganizationById(organizationId: string): Promise<Organization | null>

    countOrganizations(): Promise<number>

    findApplications(organizationId: string, pageable: Pageable): Promise<Page<Application>>

    findProjects(organizationId: string, pageable: Pageable): Promise<Page<Project>>

    /** applicationId null = the organization's members */
    findMembers(organizationId: string, applicationId: string | null, pageable: Pageable): Promise<Page<UserParticipantIdentity>>

    searchMembers(searchText: string, organizationId: string, applicationId: string | null, pageable: Pageable): Promise<Page<UserParticipantIdentity>>

    findPendingInvites(organizationId: string, applicationId: string | null, pageable: Pageable): Promise<Page<PendingInviteSummary>>

}

export class SystemOrganizationService implements ISystemOrganizationService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${SYSTEM_API_ZONE}~org.kinotic.system.api.services.SystemOrganizationService`)
    }

    public findOrganizations(pageable: Pageable): Promise<Page<Organization>> {
        return this.serviceProxy.invoke('findOrganizations', [pageable])
    }

    public searchOrganizations(searchText: string, pageable: Pageable): Promise<Page<Organization>> {
        return this.serviceProxy.invoke('searchOrganizations', [searchText, pageable])
    }

    public findOrganizationById(organizationId: string): Promise<Organization | null> {
        return this.serviceProxy.invoke('findOrganizationById', [organizationId])
    }

    public countOrganizations(): Promise<number> {
        return this.serviceProxy.invoke('countOrganizations', [])
    }

    public findApplications(organizationId: string, pageable: Pageable): Promise<Page<Application>> {
        return this.serviceProxy.invoke('findApplications', [organizationId, pageable])
    }

    public findProjects(organizationId: string, pageable: Pageable): Promise<Page<Project>> {
        return this.serviceProxy.invoke('findProjects', [organizationId, pageable])
    }

    public findMembers(organizationId: string, applicationId: string | null, pageable: Pageable): Promise<Page<UserParticipantIdentity>> {
        return this.serviceProxy.invoke('findMembers', [organizationId, applicationId, pageable])
    }

    public searchMembers(searchText: string, organizationId: string, applicationId: string | null, pageable: Pageable): Promise<Page<UserParticipantIdentity>> {
        return this.serviceProxy.invoke('searchMembers', [searchText, organizationId, applicationId, pageable])
    }

    public findPendingInvites(organizationId: string, applicationId: string | null, pageable: Pageable): Promise<Page<PendingInviteSummary>> {
        return this.serviceProxy.invoke('findPendingInvites', [organizationId, applicationId, pageable])
    }

}
