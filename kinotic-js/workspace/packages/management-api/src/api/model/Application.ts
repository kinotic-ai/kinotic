import type { Identifiable } from '@kinotic-ai/core'
import { TenantManagement } from '@/api/model/TenantManagement'

export class Application implements Identifiable<string> {

    /**
     * The slugified {@link name}, minted by the server at creation
     */
    public id!: string

    /**
     * The id of the organization that owns this application.
     * Must be set by the caller before save — backend org enforcement rejects entities
     * with a missing or mismatched organizationId.
     */
    public organizationId!: string

    public name: string

    public description: string

    /**
     * Who creates and administers this application's tenants. Under SYSTEM every APPLICATION-scope
     * user created for the application receives a tenant of its own; it applies to users created while
     * it is in force, and existing users are not backfilled.
     */
    public tenantManagement: TenantManagement = TenantManagement.TENANT_ADMINISTRATORS

    /**
     * Name of the UI whose site this application's browser flows return to, such as its OAuth
     * consent page: one of the application's published UIs, or null while none is designated. A UI
     * whose site is minted while none is designated becomes the primary UI; the owner can designate another.
     */
    public primaryUiId: string | null = null

    /**
     * Where the primary UI's site is served, or null while none is designated. Set by the
     * platform when the primary UI is designated, and moved when the site is published under
     * another sites domain; a value a caller saves is replaced.
     */
    public primaryUiUrl: string | null = null

    public updated: string | null = null

    constructor(name: string, description: string) {
        this.name = name
        this.description = description
    }

}
