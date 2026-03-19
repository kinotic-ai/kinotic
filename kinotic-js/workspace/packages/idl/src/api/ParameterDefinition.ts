import {AbstractDefinition} from '@/api/AbstractDefinition'
import {C3Type} from '@/api/C3Type'
import {C3Decorator} from '@/api/decorators/C3Decorator'

/**
 * Provides functionality to define a parameter for a {@link FunctionDefinition}.
 */
export class ParameterDefinition extends AbstractDefinition {

    /**
     * The type of this {@link ParameterDefinition}
     */
    public type: C3Type

    public constructor(name: string,
                       type: C3Type,
                       decorators?: C3Decorator[],
                       metadata?: { [p: string]: any } | null) {
        super(name, decorators, metadata)
        this.type = type
    }
}
