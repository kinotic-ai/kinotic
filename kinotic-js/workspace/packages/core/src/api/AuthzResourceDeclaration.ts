import type { AuthzRoleDeclaration } from '@/api/AuthzRoleDeclaration'

/**
 * What a service declares about the resource its functions act on, as {@link AuthzResource} declares it.
 */
export interface AuthzResourceDeclaration {
    /**
     * The resource type, a single lowercase identifier such as `report`: it names the type in the model, prefixes
     * the type's permissions (`report_can_read`) and roles (`report.editor`), and is the type of the resource every
     * function is checked on unless the function names another with {@link AuthzCheck}.
     */
    value: string
    /**
     * The type that contains this one, such as `tenant` for `report`: a grant made on a resource of the parent type
     * inherits to every resource of this type inside it, and a function naming no resource of its own, such as a
     * listing or a create, is checked on the parent. Absent for a root type.
     */
    parent?: string | null
    /**
     * The resource every function of the service is checked on, for a service whose functions act on one resource
     * the request does not name: a template over the caller's scope, `{@applicationId}` or `{@tenantId}`, or a literal
     * id. A function naming its own resource with {@link AuthzCheck} is unaffected.
     */
    resourceId?: string | null
    /**
     * The permission every function of the service needs, as a short name such as `can_manage`, for a service whose
     * functions all need the one permission whatever their names. A function declaring a permission of its own with
     * {@link AuthzCheck} keeps it. Name the service's strictest permission.
     */
    permission?: string | null
    /** Roles of this type beside the built-in ones, each bundling permissions the type's functions need. */
    roles?: AuthzRoleDeclaration[] | null
}
