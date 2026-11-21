/**
 * A role a resource service declares beside the built-in ones, as the {@link AuthzResourceDecorator} carries
 * it: the role's id and the short names of the permissions of the type it bundles.
 */
export class AuthzRoleDeclaration {

    /**
     * The role's id, `<type>.<level>`, such as `report.generator`.
     */
    public id: string

    /**
     * Short names of the type's permissions the role bundles.
     */
    public permissions: string[]

    constructor(id: string, permissions: string[]) {
        this.id = id
        this.permissions = permissions
    }
}
