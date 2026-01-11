import {NamedQueriesDefinition} from '@/api/domain/NamedQueriesDefinition'
import {Continuum, CrudServiceProxy, ICrudServiceProxy,} from '@mindignited/continuum-client'

export interface INamedQueriesService extends ICrudServiceProxy<NamedQueriesDefinition> {

    /**
     * This operation makes all the recent writes immediately available for search.
     * @return a Promise that resolves when the operation is complete
     */
    syncIndex(): Promise<void>

}

export class NamedQueriesService extends CrudServiceProxy<NamedQueriesDefinition> implements INamedQueriesService{

    constructor() {
        super(Continuum.serviceProxy('org.mindignited.structures.api.services.NamedQueriesService'))
    }

    public syncIndex(): Promise<void> {
        return this.serviceProxy.invoke('syncIndex', [])
    }
}
