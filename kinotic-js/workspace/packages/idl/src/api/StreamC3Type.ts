import {C3Type} from '@/api/C3Type'

/**
 * Represents a stream of values produced asynchronously, emitting zero or more results over time.
 * Created by Navíd Mitchell 🤪 on 7/22/26.
 */
export class StreamC3Type extends C3Type {
    /**
     * The type of each value the stream emits
     */
    public valueType: C3Type

    constructor(valueType: C3Type) {
        super('stream')
        this.valueType = valueType
    }
}
