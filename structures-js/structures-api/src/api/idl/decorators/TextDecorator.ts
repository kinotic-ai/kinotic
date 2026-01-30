import {C3Decorator} from '@kinotic/continuum-idl'

/**
 * Signals that a property is a text field, and will be full text indexed.
 * Created by Navíd Mitchell 🤪 on 4/23/23.
 */
export class TextDecorator extends C3Decorator {

    constructor() {
        super()
        this.type = 'Text'
    }
}
