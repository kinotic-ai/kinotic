import { EsIndexConfigurationData } from '@/api/decorators/EsIndexConfigurationData'
import { C3Decorator } from '@kinotic-ai/idl'

export class EsIndexConfigurationDecorator extends C3Decorator {

    public value!: EsIndexConfigurationData

    constructor() {
        super()
        this.type = 'EsIndexConfigurationDecorator'
    }
}
