import { APP_API_ZONE } from '@kinotic-ai/management-api'
import type { IKinotic, IServiceProxy } from '@kinotic-ai/core'
import type { Tenant } from '@/api/model/Tenant'

/**
 * The tenant the connected user belongs to, for an application's own pages: what it is called, and the
 * settings its administrator keeps. Every function acts on the caller's tenant, and what the caller may do is
 * checked on that tenant in the application's store.
 */
export interface ITenantService {

    /** The caller's tenant. Needs tenant_can_view. */
    getTenant(): Promise<Tenant>

    /** Renames the caller's tenant; its id stays what it was. Needs tenant_can_edit. */
    rename(name: string): Promise<Tenant>

}

export class TenantService implements ITenantService {

    private readonly serviceProxy: IServiceProxy

    constructor(kinotic: IKinotic) {
        this.serviceProxy = kinotic.serviceProxy(`${APP_API_ZONE}~org.kinotic.domain.api.services.TenantService`)
    }

    public getTenant(): Promise<Tenant> {
        return this.serviceProxy.invoke('getTenant', [])
    }

    public rename(name: string): Promise<Tenant> {
        return this.serviceProxy.invoke('rename', [name])
    }

}
