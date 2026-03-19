import {C3Decorator} from '@/api/decorators/C3Decorator'

/**
 * Interface for classes that have {@link C3Decorator}s.
 */
export interface HasDecorators {

    decorators?: C3Decorator[] | null

    /**
     * Checks if this type has a {@link C3Decorator} for the given type
     * @param value the {@link C3Decorator} to check for
     * @return true if the type has a {@link C3Decorator} for the given type false if not
     */
    containsDecorator(value: C3Decorator): boolean

    /**
     * Checks if this type has any {@link C3Decorator}
     * @return true if any {@link C3Decorator}s are present false if not
     */
    hasDecorators(): boolean

    /**
     * Adds a new decorator to this type
     * @param decorator the decorator to add
     * @return this {@link C3Base}
     */
    addDecorator(decorator: C3Decorator): void

    /**
     * Finds a decorator for the given type if it exists
     * @param value the type to find
     * @return the decorator if it exists null if not
     */
    findDecorator(value: C3Decorator): C3Decorator | null

}
