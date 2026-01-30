import {C3Decorator} from '@kinotic/continuum-idl'

/**
 * Specifies that a field should not be indexed in Elasticsearch.
 * Created By Navíd Mitchell 🤪on 3/18/25
 */
export class NotIndexedDecorator extends C3Decorator {

    constructor() {
        super()
        this.type = 'NotIndexedDecorator'
    }
}
