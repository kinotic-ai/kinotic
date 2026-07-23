import {C3Type} from '@/api/C3Type'

/**
 * Represents a value of any type: the schema intentionally places no constraint on the shape.
 * Created by Navíd Mitchell 🤪 on 7/22/26.
 */
export class AnyC3Type extends C3Type {

    constructor() {
        super('any')
    }
}
