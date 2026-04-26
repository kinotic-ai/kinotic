import type { IKinotic } from '@kinotic-ai/core'
import { CrudServiceProxy, type ICrudServiceProxy } from '@kinotic-ai/core'
import { Organization } from '@/api/model/Organization'


export interface IOrganizationService extends ICrudServiceProxy<Organization> {

    /**
     * Returns the enabled OIDC configurations registered on the given organization.
     *
     * @param organizationId the id of the organization
     * @return the enabled configurations, or an empty list if the organization is not
     *         found or has no configurations attached
     */
    getOidcConfigurations(organizationId: string): Promise<any[]>

}

export class OrganizationService extends CrudServiceProxy<Organization> implements IOrganizationService {

    constructor(kinotic: IKinotic) {
        super(kinotic.serviceProxy('org.kinotic.os.api.services.OrganizationService'))
    }

    public getOidcConfigurations(organizationId: string): Promise<any[]> {
        return this.serviceProxy.invoke('getOidcConfigurations', [organizationId])
    }

}
