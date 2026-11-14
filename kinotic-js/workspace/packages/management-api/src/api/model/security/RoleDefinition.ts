/**
 * A role as the console edits it: a name, a description and the permissions it bundles. A built-in role is
 * defined by the model, named after its type and level, and cannot be changed; a custom role is defined by an
 * organization from the permissions the model has. Every role is homed at a resource type, bundles only
 * permissions of that type and of the types inside it, and is granted only on that type or on one above it within
 * the same tier.
 */
export interface RoleDefinition {
    /** The role's id, `<resourceType>.<name>`; null for a custom role not saved yet. */
    id: string | null
    name: string
    /** What the role is for, or null. */
    description: string | null
    /** True for a role the model defines. */
    builtIn: boolean
    /** The type the role is homed at, such as project for project.editor. */
    resourceType: string
    /** The resource types of the caller's tier the role may be granted on; ignored when saving. */
    grantableOn: string[]
    /** The model names of the permissions the role bundles, such as project_can_edit. */
    permissions: string[]
}
