import { C3Decorator } from '@kinotic-ai/idl'

/**
 * Signals that a property is a text field, and will be full text indexed.
 */
export class TextDecorator extends C3Decorator {

    constructor() {
        super()
        this.type = 'Text'
    }
}
