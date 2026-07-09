import {Kinotic, type IServiceProxy} from '../src'

export interface INonExistentService {

    probablyNotHome(): Promise<void>;

}

class NonExistentService implements INonExistentService {

    private readonly serviceProxy: IServiceProxy

    constructor() {
        // Zoned into os-api so the org participant is authorized to send here and routing returns
        // NO_HANDLERS; an un-zoned address is denied at authorization and drops the connection.
        this.serviceProxy = Kinotic.serviceProxy('os-api.com.namespace.NonExistentService')
    }

    probablyNotHome(): Promise<void> {
        return this.serviceProxy.invoke('probablyNotHome')
    }
}

export const NON_EXISTENT_SERVICE: INonExistentService = new NonExistentService()
