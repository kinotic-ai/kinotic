import { MultiTenancyType } from '@/api/decorators/MultiTenancyType'
import { CalculatedPropertyConfiguration } from './CalculatedPropertyConfiguration'
import { OverrideConfiguration } from './OverrideConfiguration'
import { TransformConfiguration } from './TransformConfiguration'

/**
 * The configuration for an External Entity.
 * This allows you to specify an Entity that is part of an external codebase.
 */
export class EntityConfiguration {
    /**
     * The name of the Entity that will be used to create the Structure.
     */
    entityName!: string

    /**
     * The multi tenancy type for this Entity.
     */
    multiTenancyType!: MultiTenancyType

    /**
     * Json paths to properties that should be excluded from the Entity.
     * These have a higher priority than {@link OverrideConfiguration} and {@link TransformConfiguration}
     */
    excludeJsonPaths?: string[]

    /**
     * Array of {@link OverrideConfiguration} that should be applied to the Entity.
     * These have a higher priority than {@link TransformConfiguration}s
     */
    overrides?: OverrideConfiguration[]

    /**
     * Array of {@link TransformConfiguration} that should be applied to the Entity.
     * These have a lower priority than {@link OverrideConfiguration}s.
     */
    transforms?: TransformConfiguration[]

    /**
     * Array of {@link CalculatedPropertyConfiguration} that should be applied to the Entity.
     * The calculated property function will be called for every instance of the Entity.
     * And is passed the instance of the Entity as the first argument.
     * And the json path as the second argument.
     * These have the highest priority.
     */
    calculatedProperties?: CalculatedPropertyConfiguration[]
}
