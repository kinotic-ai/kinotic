import type { Identifiable } from '@kinotic-ai/core'

/**
 * A tenant of an application whose tenants its administrators manage (`TenantManagement.TENANT_ADMINISTRATORS`):
 * a customer of the application, whose users carry its id as their `tenantId` and whose rows are isolated
 * under it. A tenant has a login of its own: its sites, `<id>--<applicationId>--<uiName>` under the
 * organization's subdomain of the sites domain, sign its users in, and may offer providers the tenant
 * configured. An application whose tenants the platform manages gives each user a tenant at creation and
 * has no tenants here.
 */
export class Tenant implements Identifiable<string> {
    /**
     * The tenant's id, the slugified {@link name} minted at creation: lowercase letters, digits and single
     * interior dashes, since it is the first name of the tenant's site labels.
     */
    public id: string | null = null

    public organizationId: string | null = null

    /** The application the tenant belongs to, one of the organization's. */
    public applicationId: string = ''

    public name: string = ''

    public description: string | null = null

    public created: string | null = null

    public updated: string | null = null
}
