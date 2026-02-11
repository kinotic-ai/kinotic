import {EsIndexConfigurationData} from '@/api/idl/decorators/EsIndexConfigurationData.js'
import {C3Decorator} from '@kinotic/continuum-idl'

export class EsIndexConfigurationDecorator extends C3Decorator{

    public value!: EsIndexConfigurationData

    constructor() {
        super()
        this.type ='EsIndexConfigurationDecorator'
    }
}
