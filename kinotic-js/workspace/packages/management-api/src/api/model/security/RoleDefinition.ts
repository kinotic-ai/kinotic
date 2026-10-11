/**
 * A role as the console edits it: a name, a description and the permissions it bundles. A built-in role is
 * defined by the model, named after its type and level, and cannot be changed; a custom role is defined by an
 * organization from the permissions the model has.
 */
export interface RoleDefinition {
    /** The role's id, null for a custom role not saved yet. */
    id: string | null
    name: string
    /** What the role is for, or null. */
    description: string | null
    /** True for a role the model defines. */
    builtIn: boolean
    /** The model names of the permissions the role bundles, such as project_can_edit. */
    permissions: string[]
}
