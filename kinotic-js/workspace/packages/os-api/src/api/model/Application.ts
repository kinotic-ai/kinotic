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
    public updated: number | null = null

    constructor(id: string, description: string) {
        this.id = id
        this.description = description
    }

}
