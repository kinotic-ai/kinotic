import {ObjectC3Type} from '@kinotic/continuum-idl'
import {Identifiable} from '@kinotic/continuum-client'

export class Structure implements Identifiable<string> {
    public id!: string | null

    /**
     * The id of the application that this structure belongs to.
     * All application ids are unique throughout the entire system.
     */
    public applicationId!: string

    /**
     * The id of the project that this structure belongs to.
     * All project ids are unique throughout the entire system.
     */
    public projectId!: string

    public name!: string
    public entityDefinition!: ObjectC3Type
    public description?: string | null
    public created!: number // do not ever set, system managed
    public updated!: number // do not ever set, system managed
    public published!: boolean // do not ever set, system managed
    public publishedTimestamp!: number // do not ever set, system managed

    constructor(applicationId: string,
                projectId: string,
                name: string,
                entityDefinition: ObjectC3Type,
                description?: string | null) {
        this.applicationId = applicationId
        this.projectId = projectId
        this.name = name
        this.entityDefinition = entityDefinition
        this.description = description
    }

}
