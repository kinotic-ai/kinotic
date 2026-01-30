import {C3Decorator} from '@kinotic/continuum-idl'

export class CalculatedPropertyConfiguration {

    /**
     * The json path to the Entity that this property will be calculated for.
     * If this is the root Entity then this should be and empty string.
     */
    entityJsonPath!: string

    /**
     * The name of the property that will be calculated and added to the given Entity.
     */
    propertyName!: string

    /**
     * The name of the function that will be used to calculate the value for the property.
     */
    calculatedPropertyFunctionName!: string

    /**
     * Any decorators that should be applied to the calculated property.
     * These will be added to the {@link C3Type} that is created for the Entity.
     */
    decorators?: C3Decorator[]
}
