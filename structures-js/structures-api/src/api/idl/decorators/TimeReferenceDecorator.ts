import {C3Decorator} from '@mindignited/continuum-idl'

/**
 * This is used for data streams to signify the field that should be used as the time reference.
 */
export class TimeReferenceDecorator extends C3Decorator {

    constructor() {
        super()
        this.type = 'TimeReferenceDecorator'
    }
}
