import type { ObjectC3Type } from '@kinotic-ai/idl'
import type { Identifiable } from '@kinotic-ai/core'

export class EntityDefinition implements Identifiable<string> {
    public id!: string | null

    /**
     * The id of the organization that owns this entity definition.
     * Populated server-side by org enforcement on save.
     */
    public organizationId!: string

    /**
     * The id of the application that this entity definition belongs to.
     * All application ids are unique throughout the entire system.
     */
    public applicationId!: string

    /**
     * The id of the project that this entity definition belongs to.
     * All project ids are unique throughout the entire system.
     */
    public projectId!: string

    public name!: string
    public schema!: ObjectC3Type
    public description?: string | null
    public created!: number // do not ever set, system managed
    public updated!: number // do not ever set, system managed
    public published!: boolean // do not ever set, system managed
    public publishedTimestamp!: number // do not ever set, system managed

    constructor(applicationId: string,
                projectId: string,
                name: string,
                schema: ObjectC3Type,
                description?: string | null) {
        this.applicationId = applicationId
        this.projectId = projectId
        this.name = name
        this.schema = schema
        this.description = description
    }

}
