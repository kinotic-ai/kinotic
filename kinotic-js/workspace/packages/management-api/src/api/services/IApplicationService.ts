import { MANAGEMENT_API_ZONE } from '@/api/Zones'
import type { IKinotic } from '@kinotic-ai/core'
import { CrudServiceProxy, type ICrudServiceProxy } from '@kinotic-ai/core'
import { Application } from '@/api/model/Application'
import { BetaLimits } from '@/api/model/BetaLimits'
import { TenantManagement } from '@/api/model/TenantManagement'


export interface IApplicationService extends ICrudServiceProxy<Application> {

    /**
     * Creates a new application if it does not already exist, deriving its id from the
     * slugified name. The organization id is derived from the authenticated participant on
     * the server.
     * @param name the name of the application to create
     * @param description the description of the application to create
     * @param tenantManagement who creates and administers the application's tenants, or omitted
     *        for TENANT_ADMINISTRATORS. SYSTEM applies to users created while it is in force, so
     *        it is chosen before the application has users
     * @return {@link Promise} emitting the created application, or the existing application
     *         whose id matches the slugified name
     */
    createApplicationIfNotExist(name: string, description: string, tenantManagement?: TenantManagement): Promise<Application>

    /**
     * Returns the free Kinotic beta cloud plan the caller's organization is on: the most
     * applications it may have, the most microservices each application may run, how many of each
     * it uses, and a summary of the plan to tell the person using it.
     * @return {@link Promise} emitting the plan, or null when the server imposes no limits
     */
    findBetaLimits(): Promise<BetaLimits | null>

    /**
     * This operation makes all the recent writes immediately available for search.
     * @return a Promise that resolves when the operation is complete
     */
    syncIndex(): Promise<void>

}

export class ApplicationService extends CrudServiceProxy<Application> implements IApplicationService {

    constructor(kinotic: IKinotic) {
        super(kinotic.serviceProxy(`${MANAGEMENT_API_ZONE}~org.kinotic.management.api.services.ApplicationService`))
    }

    public createApplicationIfNotExist(name: string, description: string, tenantManagement?: TenantManagement): Promise<Application> {
        return this.serviceProxy.invoke('createApplicationIfNotExist', [name, description, tenantManagement])
    }

    public findBetaLimits(): Promise<BetaLimits | null> {
        return this.serviceProxy.invoke('findBetaLimits', [])
    }

    public syncIndex(): Promise<void> {
        return this.serviceProxy.invoke('syncIndex', [])
    }

}
