import {C3Decorator} from '@/api/decorators/C3Decorator'

/**
 * Signifies that the decorated value must not be null
 * Created by Navíd Mitchell 🤪 on 4/23/23.
 */
export class NotNullDecorator extends C3Decorator {

    constructor() {
        super()
        this.type = 'NotNull'
    }
}
