import { MANAGEMENT_API_ZONE } from '@/api/Zones'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import { FunctionalIterablePage, type IterablePage, type Page, type Pageable } from '@kinotic-ai/core'
import { Tenant } from '@/api/model/Tenant'

/**
 * Manages the {@link Tenant}s of the caller's organization's applications. A tenant's id is derived from its
 * slugified name at creation: lowercase letters, digits, and interior dashes, at most 24 characters so it leads
 * the tenant's site labels, and unique within its application. Every operation names the application, since two
 * applications may each have a tenant of the same id.
 */
export interface ITenantService {

    /**
     * Creates a tenant of the application if it does not already exist, deriving its id from the slugified
     * name. The organization id is derived from the authenticated participant on the server.
     * @param applicationId the application the tenant belongs to, one of the caller's organization's
     * @param name the name of the tenant to create
     * @param description the description of the tenant to create
     * @return the created tenant, or the application's existing tenant whose id matches the slugified name
     */
    createTenantIfNotExist(applicationId: string, name: string, description: string | null): Promise<Tenant>

    /** The application's tenant of the id, or null when the application has none. */
    findById(applicationId: string, tenantId: string): Promise<Tenant | null>

    /** The number of the application's tenants. */
    countForApplication(applicationId: string): Promise<number>

    /** The tenants of the application, a page at a time. */
    findAllForApplication(applicationId: string, pageable: Pageable): Promise<IterablePage<Tenant>>

    /**
     * The tenants of the application whose name or id starts with prefix, ignoring case, a page at a time; a blank
     * prefix finds them all, as {@link findAllForApplication} does.
     */
    findForApplicationByPrefix(applicationId: string, prefix: string, pageable: Pageable): Promise<IterablePage<Tenant>>

    /**
     * Deletes the application's tenant of the id, with its email and password sign-in. A tenant created again under
     * the same id starts with no password sign-in of its own. No-op when the application has no tenant of the id.
     */
    deleteById(applicationId: string, tenantId: string): Promise<void>

}

export class TenantService implements ITenantService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.TenantService`)
    }

    public createTenantIfNotExist(applicationId: string, name: string, description: string | null): Promise<Tenant> {
        return this.serviceProxy.invoke('createTenantIfNotExist', [applicationId, name, description])
    }

    public findById(applicationId: string, tenantId: string): Promise<Tenant | null> {
        return this.serviceProxy.invoke('findById', [applicationId, tenantId])
    }

    public countForApplication(applicationId: string): Promise<number> {
        return this.serviceProxy.invoke('countForApplication', [applicationId])
    }

    public async findAllForApplication(applicationId: string, pageable: Pageable): Promise<IterablePage<Tenant>> {
        const page: Page<Tenant> = await this.findAllForApplicationSinglePage(applicationId, pageable)
        return new FunctionalIterablePage(pageable, page,
            (pageable: Pageable) => this.findAllForApplicationSinglePage(applicationId, pageable))
    }

    private findAllForApplicationSinglePage(applicationId: string, pageable: Pageable): Promise<Page<Tenant>> {
        return this.serviceProxy.invoke('findAllForApplication', [applicationId, pageable])
    }

    public async findForApplicationByPrefix(applicationId: string, prefix: string, pageable: Pageable): Promise<IterablePage<Tenant>> {
        const page: Page<Tenant> = await this.serviceProxy.invoke('findForApplicationByPrefix', [applicationId, prefix, pageable])
        return new FunctionalIterablePage(pageable, page,
            (next: Pageable) => this.serviceProxy.invoke('findForApplicationByPrefix', [applicationId, prefix, next]))
    }

    public deleteById(applicationId: string, tenantId: string): Promise<void> {
        return this.serviceProxy.invoke('deleteById', [applicationId, tenantId])
    }

}
