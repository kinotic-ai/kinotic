import {C3Type} from '@/api/C3Type'

/**
 * Represents a value that is produced asynchronously, resolving to a single result.
 * Created by Navíd Mitchell 🤪 on 7/22/26.
 */
export class AsyncC3Type extends C3Type {
    /**
     * The type of the value the asynchronous invocation resolves to
     */
    public valueType: C3Type

    constructor(valueType: C3Type) {
        super('async')
        this.valueType = valueType
    }
}
