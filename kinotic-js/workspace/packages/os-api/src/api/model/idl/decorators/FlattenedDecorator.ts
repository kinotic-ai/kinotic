import { C3Decorator } from '@kinotic-ai/idl'

/**
 * Signifies that an object should be stored as a flattened json.
 * This will not be indexed, and thus not searchable.
 */
export class FlattenedDecorator extends C3Decorator {

    constructor() {
        super()
        this.type = 'Flattened'
    }
}
