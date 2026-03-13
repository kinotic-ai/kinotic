import {AbstractDefinition} from '@/api/AbstractDefinition'
import {ParameterDefinition} from '@/api/ParameterDefinition'
import { C3Type } from '@/api/C3Type'
import {C3Decorator} from '@/api/decorators/C3Decorator'
import {MetadataType} from '@/api/HasMetadata'
import { VoidC3Type } from '@/api/VoidC3Type'

/**
 * Provides functionality to define a function with a Continuum schema.
 * Created by navid on 2023-4-13
 */
export class FunctionDefinition extends AbstractDefinition {

    /**
     * This is the C3Type that defines the return type of this function.
     */
    public returnType: C3Type = new VoidC3Type()

    /**
     * This array defines the {@link C3Type}s that define the arguments for this function.
     * Argument names must be unique.
     */
    public parameters: ParameterDefinition[] = []


    constructor(name: string,
                decorators?: C3Decorator[] | null,
                metadata?: MetadataType | null) {
        super(name, decorators, metadata)
    }

    /**
     * Adds a new {@link ParameterDefinition} to this {@link FunctionDefinition}
     * {@link ParameterDefinition} must have unique names
     * @param name the name of the parameter
     * @param c3Type the type of the parameter
     * @param decorators the decorators to apply to the parameter
     */
    public addParameter(name: string, c3Type: C3Type, decorators?: C3Decorator[]): FunctionDefinition {
        const param = new ParameterDefinition(name, c3Type, decorators)
        if(this.parameters.find((value) => value.name === name)){
            throw new Error(`FunctionDefinition already contains parameter for name ${name}`);
        }
        this.parameters.push(param)
        return this
    }

}
