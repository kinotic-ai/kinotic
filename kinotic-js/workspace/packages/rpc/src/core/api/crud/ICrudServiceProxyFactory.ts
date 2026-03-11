

import { ICrudServiceProxy } from './ICrudServiceProxy'
import { Identifiable } from '@/index'

/**
 * Produces {@link ICrudServiceProxy} Proxies for a known remote CRUD service
 */
export interface ICrudServiceProxyFactory {

    /**
     * Produces a {@link ICrudServiceProxy} for the given serviceIdentifier
     * @param serviceIdentifier the service identifier to produce a proxy for
     */
    crudServiceProxy<T extends Identifiable<string>>(serviceIdentifier: string): ICrudServiceProxy<T>

}
