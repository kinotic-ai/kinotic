import type { Identifiable } from '@kinotic-ai/core'

export class Application implements Identifiable<string> {
    public id: string

    /**
     * The id of the organization that owns this application.
     * Must be set by the caller before save — backend org enforcement rejects entities
     * with a missing or mismatched organizationId.
     */
    public organizationId!: string

    public description: string

    /**
     * When true, every APPLICATION-scope user created for this application receives an
     * auto-generated unique tenantId, isolating each user's SHARED entity data in its own
     * tenant. Applies only to users created after it is enabled.
     */
    public tenantPerUser: boolean = false

    public updated: number | null = null

    constructor(id: string, description: string) {
        this.id = id
        this.description = description
    }

}
