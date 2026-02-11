import {C3Decorator} from '@kinotic/continuum-idl'

/**
 * Specifies the discriminator field for polymorphic entities.
 */
export class DiscriminatorDecorator extends C3Decorator {
    public propertyName?: string
    constructor() {
        super()
        this.type = 'Discriminator'
    }

    public withPropertyName(propertyName: string): DiscriminatorDecorator {
        this.propertyName = propertyName
        return this
    }
}
