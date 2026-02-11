import {Continuum, CrudServiceProxy, ICrudServiceProxy,} from '@kinotic/continuum-client'
import {Application} from '@/api/domain/Application'


export interface IApplicationService extends ICrudServiceProxy<Application> {

    /**
     * Creates a new application if it does not already exist.
     * @param id the id of the application to create
     * @param description the description of the application to create
     * @return {@link Promise} emitting the created application
     */
    createApplicationIfNotExist(id: string, description: string): Promise<Application>

    /**
     * This operation makes all the recent writes immediately available for search.
     * @return a Promise that resolves when the operation is complete
     */
    syncIndex(): Promise<void>

}

export class ApplicationService extends CrudServiceProxy<Application> implements IApplicationService{

    constructor() {
        super(Continuum.serviceProxy('org.kinotic.structures.api.services.ApplicationService'))
    }

    public createApplicationIfNotExist(id: string, description: string): Promise<Application> {
        return this.serviceProxy.invoke('createApplicationIfNotExist', [id, description])
    }

    public syncIndex(): Promise<void> {
        return this.serviceProxy.invoke('syncIndex', [])
    }

}
