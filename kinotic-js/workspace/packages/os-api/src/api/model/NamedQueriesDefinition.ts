import type { Identifiable } from '@kinotic-ai/core'
import type { FunctionDefinition } from '@kinotic-ai/idl'

/**
 * Provides Metadata that represents Named Queries for an Application
 */
export class NamedQueriesDefinition implements Identifiable<string> {
    public id: string
    public applicationId: string
    public projectId: string
    public entityDefinitionName: string
    public namedQueries: FunctionDefinition[]

    constructor(id: string,
                applicationId: string,
                projectId: string,
                entityDefinitionName: string,
                namedQueries: FunctionDefinition[]) {
        this.id = id;
        this.applicationId = applicationId;
        this.projectId = projectId;
        this.entityDefinitionName = entityDefinitionName;
        this.namedQueries = namedQueries;
    }

}
