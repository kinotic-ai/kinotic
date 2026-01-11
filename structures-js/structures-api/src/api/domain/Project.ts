import { Identifiable } from '@mindignited/continuum-client'
import { ProjectType } from '@/api/domain/ProjectType'

export class Project implements Identifiable<string> {
    /**
     * The id of the project.
     * All project ids are unique throughout the entire system.
     */
    public id: string | null = null

    /**
     * The id of the application that this project belongs to.
     * All application ids are unique throughout the entire system.
     */
    public applicationId: string

    /**
     * The name of the project.
     */
    public name: string

    /**
     * The description of the project.
     */
    public description?: string

    /**
     * The source of truth for the project.
     */
    public sourceOfTruth: ProjectType | null = null

    /**
     * The date and time the project was updated.
     */
    public updated: number | null = null

    constructor(id: string | null, applicationId: string, name: string, description?: string) {
        this.id = id
        this.applicationId = applicationId
        this.name = name
        this.description = description
    }
}
