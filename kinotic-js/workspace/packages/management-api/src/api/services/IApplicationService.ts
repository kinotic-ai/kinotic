import { MANAGEMENT_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic } from '@kinotic-ai/core'
import { CrudServiceProxy, type ICrudServiceProxy } from '@kinotic-ai/core'
import { Application } from '@/api/model/Application'
import { TenantManagement } from '@/api/model/TenantManagement'


export interface IApplicationService extends ICrudServiceProxy<Application> {

    /**
     * Creates a new application if it does not already exist, deriving its id from the
     * slugified name. The organization id is derived from the authenticated participant on
     * the server.
     * @param name the name of the application to create
     * @param description the description of the application to create
     * @param tenantManagement who creates and administers the application's tenants, or omitted
     *        for TENANT_ADMINISTRATORS. PLATFORM applies to users created while it is in force, so
     *        it is chosen before the application has users
     * @return {@link Promise} emitting the created application, or the existing application
     *         whose id matches the slugified name
     */
    createApplicationIfNotExist(name: string, description: string, tenantManagement?: TenantManagement): Promise<Application>

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

    public syncIndex(): Promise<void> {
        return this.serviceProxy.invoke('syncIndex', [])
    }

}
