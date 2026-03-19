import { C3Decorator } from '@kinotic-ai/idl'

/**
 * Specifies that a field should not be indexed in Elasticsearch.
 */
export class NotIndexedDecorator extends C3Decorator {

    constructor() {
        super()
        this.type = 'NotIndexedDecorator'
    }
}
