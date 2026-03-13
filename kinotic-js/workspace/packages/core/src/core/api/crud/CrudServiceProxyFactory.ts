import type {ICrudServiceProxyFactory} from './ICrudServiceProxyFactory'
import type {ICrudServiceProxy} from './ICrudServiceProxy'
import { CrudServiceProxy } from './CrudServiceProxy'
import type {Identifiable} from '@/index'
import type {IServiceRegistry} from '@/core/api/IServiceRegistry'


/**
 * Default implementation of {@link ICrudServiceProxyFactory}
 */
export class CrudServiceProxyFactory implements ICrudServiceProxyFactory {

    private serviceRegistry: IServiceRegistry

    constructor(serviceRegistry: IServiceRegistry) {
        this.serviceRegistry = serviceRegistry
    }

    public crudServiceProxy<T extends Identifiable<string>>(serviceIdentifier: string): ICrudServiceProxy<T> {
        if ( typeof serviceIdentifier === 'undefined' || serviceIdentifier.length === 0 ) {
            throw new Error('The serviceIdentifier provided must contain a value')
        }
        return new CrudServiceProxy<T>(this.serviceRegistry.serviceProxy(serviceIdentifier))
    }

}
