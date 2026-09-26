import type { Identifiable } from '@kinotic-ai/core'

export class Application implements Identifiable<string> {

    /**
     * The slugified {@link name}, minted by the server at creation
     */
    public id!: string

    /**
     * The id of the organization that owns this application.
     * Must be set by the caller before save — backend org enforcement rejects entities
     * with a missing or mismatched organizationId.
     */
    public organizationId!: string

    public name: string

    public description: string

    /**
     * When true, every APPLICATION-scope user created for this application receives an
     * auto-generated unique tenantId, isolating each user's SHARED entity data in its own
     * tenant. Applies only to users created after it is enabled.
     */
    public tenantPerUser: boolean = false

    /**
     * Name of the UI whose site this application's browser flows return to, such as its OAuth
     * consent page: one of the application's published UIs, or null until the owner designates one.
     */
    public primaryUiId: string | null = null

    /**
     * Where the primary UI's site is served, or null while none is designated. Set by the
     * platform when the owner designates the primary UI; a value a caller saves is replaced.
     */
    public primaryUiUrl: string | null = null

    public updated: number | null = null

    constructor(name: string, description: string) {
        this.name = name
        this.description = description
    }

}
