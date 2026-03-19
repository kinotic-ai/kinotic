import {C3Type} from '@/api/C3Type'

/**
 * The boolean type matches only two special values: true and false.
 * Note that values that evaluate to true or false, such as 1 and 0, are not accepted by the schema.
 * <p>
 * Created by navid on 2023-4-13.
 */
export class BooleanC3Type extends C3Type {

    constructor() {
        super('boolean')
    }
}
