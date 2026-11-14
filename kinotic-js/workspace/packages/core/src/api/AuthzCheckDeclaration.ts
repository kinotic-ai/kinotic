/**
 * What a function declares about its check, as {@link AuthzCheck} declares it; whatever is left out is derived
 * from the function's name and parameters, as described on {@link AuthzResource}.
 */
export interface AuthzCheckDeclaration {
    /**
     * The permission the function needs, as a short name such as `can_generate`. In the model it is prefixed by
     * the type it is about, so `can_generate` on a `report` service is `report_can_generate`.
     */
    permission?: string | null
    /**
     * The type of the resource the check is made on, when it is not the service's own type, such as the parent for a
     * create. A type name, never a template.
     */
    resource?: string | null
    /**
     * The id of the resource the check is made on, as a template over the function's parameters and the caller's
     * scope: `{reportId}` is a parameter's value, `{registration.id}` a property of an object parameter, and
     * `{@applicationId}` or `{@tenantId}` the matching id of the caller's scope.
     */
    resourceId?: string | null
    /**
     * Short names of permissions on the same type that this permission also grants, so a role bundling
     * `can_generate` declared with `implies: ['can_read']` reads what it generates.
     */
    implies?: string[] | null
    /**
     * True for a function whose check must not be answered from the engine's caches, because a stale allow would
     * be a security event.
     */
    consistent?: boolean
}
