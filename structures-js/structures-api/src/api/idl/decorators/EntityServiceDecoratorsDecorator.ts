import {EntityServiceDecoratorsConfig} from '@/api/idl/decorators/EntityServiceDecoratorsConfig'
import {C3Decorator} from '@mindignited/continuum-idl'

/**
 * Provides a way to add decorators to an entity service
 */
export class EntityServiceDecoratorsDecorator extends C3Decorator {

    public config!: EntityServiceDecoratorsConfig

    constructor() {
        super()
        this.type = 'EntityServiceDecoratorsDecorator'
    }

}
