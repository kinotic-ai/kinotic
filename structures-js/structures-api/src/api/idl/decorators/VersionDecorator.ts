import {C3Decorator} from '@mindignited/continuum-idl'

/**
 * Signifies the Version field of an entity, The value must be a string and will be automatically populated by the server.
 * When used this will enable optimistic locking for a Structure / Entity
 */
export class VersionDecorator extends C3Decorator {

    constructor() {
        super()
        this.type = 'VersionDecorator'
    }
}
