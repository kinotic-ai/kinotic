import type { EntityServiceDecoratorsConfig } from './EntityServiceDecoratorsConfig'
import { C3Decorator } from '@kinotic-ai/idl'

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
