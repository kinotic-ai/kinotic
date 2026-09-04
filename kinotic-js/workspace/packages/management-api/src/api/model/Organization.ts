import type { Identifiable } from '@kinotic-ai/core'
import type { OrganizationStorage } from '@/api/model/OrganizationStorage'

/**
 * Represents an organization developing applications on the Kinotic OS platform.
 * Organizations provide the boundary for teams, applications, users, and shared OIDC configuration.
 *
 * The {@link id} is auto-generated from the {@link name} on save (slugified) and serves as the URL-safe identifier.
 */
export class Organization implements Identifiable<string> {
    public id: string | null = null
    public name: string = ''
    public description: string | null = null
    public oidcConfigurationIds: string[] | null = null
    public createdBy: string | null = null
    /**
     * The organization's storage, or null until provisioning has recorded it.
     */
    public storage: OrganizationStorage | null = null
    /**
     * Id of the job run that last provisioned the organization, or null before the first one
     * started.
     */
    public provisioningJobRunId: string | null = null
    public created: number | null = null
    public updated: number | null = null
}
