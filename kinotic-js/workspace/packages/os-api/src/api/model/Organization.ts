import type { Identifiable } from '@kinotic-ai/core'

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
    public created: number | null = null
    public updated: number | null = null
}
