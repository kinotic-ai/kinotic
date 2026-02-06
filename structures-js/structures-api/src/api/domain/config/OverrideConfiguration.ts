import {PropertyDefinition} from '@kinotic/continuum-idl'

/**
 * The configuration for a property that should be overridden.
 * This allows you to specify a static {@link PropertyDefinition} for a property.
 */
export class OverrideConfiguration {
    /**
     * The json path to the property that should be overridden.
     */
    jsonPath!: string

    /**
     * The {@link PropertyDefinition} that should be used for the property.
     */
    propertyDefinition!: PropertyDefinition
}
