import {AuthzRoleDeclaration} from '@/api/decorators/AuthzRoleDeclaration'
import {C3Decorator} from '@/api/decorators/C3Decorator'

/**
 * Marks a service as acting on one authorization resource type: the service's functions are checked against
 * that type, and the type, with its containment parent and the roles it declares, is part of the generated
 * authorization model.
 */
export class AuthzResourceDecorator extends C3Decorator {

    /**
     * The resource type the service acts on, such as `report`.
     */
    public resourceType: string

    /**
     * The type that contains this one and from which grants inherit, or null for a root type.
     */
    public parent: string | null = null

    /**
     * The object every function of the service is checked on, as a template over the caller's scope, unless the
     * function declares its own; null when each function's object is derived from what it names.
     */
    public objectId: string | null = null

    /**
     * The permission every function of the service requires unless it declares its own; null when each
     * function's permission is derived from its name.
     */
    public permission: string | null = null

    /**
     * The roles the service declares for its type beside the built-in ones; empty when it declares none.
     */
    public roles: AuthzRoleDeclaration[] = []

    constructor(resourceType: string) {
        super()
        this.type = 'AuthzResource'
        this.resourceType = resourceType
    }
}
