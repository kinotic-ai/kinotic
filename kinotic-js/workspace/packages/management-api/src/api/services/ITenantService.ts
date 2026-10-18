import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic } from '@kinotic-ai/core'
import { CrudServiceProxy, FunctionalIterablePage, type ICrudServiceProxy, type IterablePage, type Page, type Pageable } from '@kinotic-ai/core'
import { Tenant } from '@/api/model/Tenant'

/**
 * Manages the {@link Tenant}s of the caller's organization's applications. A tenant's id is derived from its
 * slugified name at creation: lowercase letters, digits, and interior dashes, short enough to lead the
 * tenant's site labels.
 */
export interface ITenantService extends ICrudServiceProxy<Tenant> {

    /**
     * Creates a tenant of the application if it does not already exist, deriving its id from the slugified
     * name. The organization id is derived from the authenticated participant on the server.
     * @param applicationId the application the tenant belongs to, one of the caller's organization's
     * @param name the name of the tenant to create
     * @param description the description of the tenant to create
     * @return the created tenant, or the application's existing tenant whose id matches the slugified name
     */
    createTenantIfNotExist(applicationId: string, name: string, description: string | null): Promise<Tenant>

    /** The tenants of the application, a page at a time. */
    findAllForApplication(applicationId: string, pageable: Pageable): Promise<IterablePage<Tenant>>

}

export class TenantService extends CrudServiceProxy<Tenant> implements ITenantService {

    constructor(kinotic: IKinotic) {
        super(kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.TenantService`))
    }

    public createTenantIfNotExist(applicationId: string, name: string, description: string | null): Promise<Tenant> {
        return this.serviceProxy.invoke('createTenantIfNotExist', [applicationId, name, description])
    }

    public async findAllForApplication(applicationId: string, pageable: Pageable): Promise<IterablePage<Tenant>> {
        const page: Page<Tenant> = await this.findAllForApplicationSinglePage(applicationId, pageable)
        return new FunctionalIterablePage(pageable, page,
            (pageable: Pageable) => this.findAllForApplicationSinglePage(applicationId, pageable))
    }

    private findAllForApplicationSinglePage(applicationId: string, pageable: Pageable): Promise<Page<Tenant>> {
        return this.serviceProxy.invoke('findAllForApplication', [applicationId, pageable])
    }

}
