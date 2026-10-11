/**
 * A resource in the authorization store, the node a grant is made on or a check is made against.
 */
export interface Resource {
    /** The resource type, as its service declares it: organization, application, project, entity_definition. */
    type: string
    /** The resource's id. */
    id: string
}
