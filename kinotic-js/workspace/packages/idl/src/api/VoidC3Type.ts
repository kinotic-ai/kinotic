import {C3Type} from '@/api/C3Type'

/**
 * This is a type to represent a void type.
 * Void types support the values null or void.
 *
 * Created by navid on 2023-4-13.
 */
export class VoidC3Type extends C3Type {

    constructor() {
        super('void')
    }
}
