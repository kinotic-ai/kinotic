import {C3Decorator} from '@kinotic/continuum-idl'

/**
 * Denotes the field that will hold the tenant id to use as the Multi Tenant discriminator field
 * When used this will enable optimistic locking for a Structure / Entity
 */
export class TenantIdDecorator extends C3Decorator {

    constructor() {
        super()
        this.type = 'TenantIdDecorator'
    }
}
