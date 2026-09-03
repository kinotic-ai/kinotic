import { C3Decorator } from '@kinotic-ai/idl'

/**
 * Marks a property as a nested object.
 */
export class NestedDecorator extends C3Decorator {

    constructor() {
        super()
        this.type = 'Nested'
    }
}
