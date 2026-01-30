import {IntC3Type, ObjectC3Type, StringC3Type} from '@kinotic/continuum-idl'

/**
 * Represents a {@link QueryOptions} type.
 * This is used to specify options that will be used when a Named Query is executed.
 */
export class QueryOptionsC3Type extends ObjectC3Type {

    public readonly type: string = 'queryOptions'

    constructor() {
        super('QueryOptions', 'org.mindignited.structures.domain')
        this.addProperty('timeZone', new StringC3Type())
        this.addProperty('requestTimeout', new IntC3Type())
        this.addProperty('pageTimeout', new StringC3Type())
    }
}
