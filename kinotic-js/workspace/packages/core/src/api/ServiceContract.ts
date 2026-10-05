/**
 * A role a service declares for its resource type, named `<type>.<level>`, bundling permissions of the type.
 */
export interface AuthzRoleDeclaration {
    /** The role's id, `<type>.<level>` with a lowercase identifier as the level. */
    id: string
    /** The short names of the type's permissions the role bundles, such as `can_generate`. */
    permissions: string[]
}

/**
 * What a service declares about the resource its functions act on, as {@link AuthzResource} declares it.
 */
export interface AuthzResourceDeclaration {
    /** The resource type, a lowercase identifier such as `report`. */
    value: string
    /** The type the resource sits under, such as `tenant` or `application`; absent for a root type. */
    parent?: string | null
    /** The id template every function is checked on, such as `{@applicationId}`, when the service acts on one object. */
    objectId?: string | null
    /** The permission every function of the service needs, when they all need the same one. */
    permission?: string | null
    /** The roles the service declares for its type. */
    roles?: AuthzRoleDeclaration[] | null
}

/**
 * What a function declares about its check, as {@link AuthzCheck} declares it; whatever is left out is derived
 * from the function's name and parameters.
 */
export interface AuthzCheckDeclaration {
    /** The short permission name, such as `can_generate`. */
    permission?: string | null
    /** The type of the object the check is made on, when it is not the service's own. */
    resource?: string | null
    /** The id template of the object, over the function's parameters and the caller's scope, such as `{reportId}`. */
    objectId?: string | null
    /** Short names of permissions this one implies. */
    implies?: string[] | null
    /** True for a function any caller the zone admits may call, which carries no check. */
    zoneOnly?: boolean
    /** True for a check that must answer from the stored relationships rather than the engine's caches. */
    consistent?: boolean
}

/**
 * One function of a service's contract: its name, the names of the parameters a request carries, in order,
 * and what it declares about its check.
 */
export interface FunctionContract {
    name: string
    parameters: string[]
    check?: AuthzCheckDeclaration | null
}

/**
 * The contract of a service a runtime publishes, as the platform's directory stores it: where the service is
 * addressed, the resource it declares and its functions. The platform derives each function's check from it,
 * generates the application's authorization model with it, and checks every request to the service against it.
 */
export interface ServiceContract {
    namespace: string | null
    name: string
    version: string | null
    zone: string
    resource: AuthzResourceDeclaration
    functions: FunctionContract[]
}
