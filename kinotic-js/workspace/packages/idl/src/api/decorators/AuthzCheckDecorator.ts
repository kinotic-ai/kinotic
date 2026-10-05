import {C3Decorator} from '@/api/decorators/C3Decorator'

/**
 * The authorization check a function requires. A service's definition declares what the derivation cannot
 * read from the function's name and parameters: a permission of its own, another object than the derived
 * one, the permissions it implies, a consistent answer, or that any caller the zone admits may call it. The
 * platform derives the rest when the service registers, naming the object checked and the type the permission
 * is named for.
 */
export class AuthzCheckDecorator extends C3Decorator {

    /**
     * The type of the object the check is made on, when it is not the derived one.
     */
    public resource: string | null = null

    /**
     * The id of the object the check is made on, as a template over the function's parameters and the
     * caller's scope, such as `{reportId}` or `{@tenantId}`.
     */
    public objectId: string | null = null

    /**
     * The type the permission is named for, which the platform derives.
     */
    public permissionResource: string | null = null

    /**
     * The short permission name, such as `can_generate`.
     */
    public permission: string | null = null

    /**
     * Short names of permissions on the permission's type that this one implies.
     */
    public implies: string[] = []

    /**
     * True for a function declared zone-only, which any caller the zone admits may call and which carries no
     * check.
     */
    public zoneOnly: boolean = false

    /**
     * Whether the check must answer from the stored relationships rather than the engine's caches.
     */
    public consistent: boolean = false

    constructor() {
        super()
        this.type = 'AuthzCheck'
    }
}
