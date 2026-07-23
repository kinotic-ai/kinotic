import { OS_API_ZONE } from '@/api/PlatformZones'
import type { IKinotic } from '@kinotic-ai/core'
import { CrudServiceProxy, type ICrudServiceProxy } from '@kinotic-ai/core'
import type { NamedQueriesDefinition } from '@/api/model/NamedQueriesDefinition'

export interface INamedQueriesDefinitionService extends ICrudServiceProxy<NamedQueriesDefinition> {

    /**
     * This operation makes all the recent writes immediately available for search.
     * @return a Promise that resolves when the operation is complete
     */
    syncIndex(): Promise<void>

}

export class NamedQueriesDefinitionService extends CrudServiceProxy<NamedQueriesDefinition> implements INamedQueriesDefinitionService {

    constructor(kinotic: IKinotic) {
        super(kinotic.serviceProxy(`${OS_API_ZONE}~org.kinotic.persistence.api.services.NamedQueriesDefinitionService`))
    }

    public syncIndex(): Promise<void> {
        return this.serviceProxy.invoke('syncIndex', [])
    }
}
