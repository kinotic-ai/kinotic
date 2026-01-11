import {EntityType} from '@/api/idl/decorators/EntityType.js'
import {C3Decorator} from '@mindignited/continuum-idl'
import {MultiTenancyType} from '@/api/idl/decorators/MultiTenancyType'

/**
 * Signifies that a class is an entity.
 */
export class EntityDecorator extends C3Decorator {
    public multiTenancyType: MultiTenancyType = MultiTenancyType.NONE
    public entityType: EntityType = EntityType.TABLE
    constructor() {
        super()
        this.type = 'Entity'
    }

    public withMultiTenancyType(type: MultiTenancyType): EntityDecorator {
        this.multiTenancyType = type
        return this
    }

    public withEntityType(type: EntityType): EntityDecorator {
        this.entityType = type
        return this
    }
}
