import {C3Decorator} from '@kinotic/continuum-idl'

/**
 * Signifies that an object should be stored as a flattened json.
 * This will not be indexed, and thus not searchable.
 * Created by Navíd Mitchell 🤪 on 4/23/23.
 */
export class FlattenedDecorator extends C3Decorator {

    constructor() {
        super()
        this.type = 'Flattened'
    }
}
