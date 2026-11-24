/**
 * A role a service declares for its resource type beside the built-in `viewer`, `editor` and `admin`: a bundle
 * of the type's own permissions, defined by the generated model, named like a built-in role and granted the
 * same way.
 */
export interface AuthzRoleDeclaration {
    /** The role's id, `<type>.<level>`, with a lowercase identifier no built-in role of the type uses as the level. */
    id: string
    /** The short names of the permissions the role bundles, each one a permission a function of the type needs. */
    permissions: string[]
}
