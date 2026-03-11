import {Continuum, IServiceProxy} from '../src'

export interface INonExistentService {

    probablyNotHome(): Promise<void>;

}

class NonExistentService implements INonExistentService {

    private readonly serviceProxy: IServiceProxy

    constructor() {
        this.serviceProxy = Continuum.serviceProxy('com.namespace.NonExistentService')
    }

    probablyNotHome(): Promise<void> {
        return this.serviceProxy.invoke('probablyNotHome')
    }
}

export const NON_EXISTENT_SERVICE: INonExistentService = new NonExistentService()
