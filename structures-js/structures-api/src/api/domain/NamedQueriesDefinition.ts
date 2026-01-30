import { Identifiable } from '@kinotic/continuum-client'
import {FunctionDefinition} from '@kinotic/continuum-idl'

/**
 * Provides Metadata that represents Named Queries for a Application
 */
export class NamedQueriesDefinition implements Identifiable<string> {
    public id: string
    public applicationId: string
    public projectId: string
    public structure: string
    public namedQueries: FunctionDefinition[]

    constructor(id: string,
                applicationId: string,
                projectId: string,
                structure: string,
                namedQueries: FunctionDefinition[]) {
        this.id = id;
        this.applicationId = applicationId;
        this.projectId = projectId;
        this.structure = structure;
        this.namedQueries = namedQueries;
    }

}

