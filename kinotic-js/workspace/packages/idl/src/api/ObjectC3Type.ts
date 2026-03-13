import {ComplexC3Type} from '@/api/ComplexC3Type'
import { C3Type } from '@/api/C3Type'
import {C3Decorator} from '@/api/decorators/C3Decorator'
import {MetadataType} from '@/api/HasMetadata'
import {PropertyDefinition} from '@/api/PropertyDefinition'

/**
 * ObjectC3Type is used to define a complex object type in the Continuum IDL.
 * Properties are defined with {@link PropertyDefinition}s
 * The context for equality here is the namespace and name.
 * Given no two object types can have the same namespace and name this is the only context needed for equality.
 */
export class ObjectC3Type extends ComplexC3Type {

    /**
     * The parent schema of this object definition.
     * This is used to support inheritance.
     */
    public parent?: ObjectC3Type | null = null

    /**
     * The properties are the fields of this object type.
     */
    public properties: PropertyDefinition[] = []

    constructor(name: string,
                namespace: string,
                decorators?: C3Decorator[] | null,
                metadata?: MetadataType | null) {
        super('object', name, namespace, decorators, metadata)
    }

    public addProperty(name: string, c3Type: C3Type, decorators?: C3Decorator[]): ObjectC3Type {
        return this.addPropertyDefinition(new PropertyDefinition(name, c3Type, decorators))
    }


    /**
     * Adds a property to this {@link ObjectC3Type}
     * @param propertyDefinition to add to this {@link ObjectC3Type}
     * @return this {@link ObjectC3Type} for chaining
     */
    public addPropertyDefinition(propertyDefinition: PropertyDefinition): ObjectC3Type {
        this.properties.push(propertyDefinition);
        return this;
    }

    /**
     * Finds the first {@link PropertyDefinition} for the given name
     * @param name the property to find
     * @return the {@link PropertyDefinition}  if it exists null if not
     */
    public findProperty(name: string): PropertyDefinition | null {
        let ret: PropertyDefinition | null = null
        if (this.properties) {
            for (const property of this.properties) {
                if(property.name === name) {
                    ret = property
                    break
                }
            }
        }
        return ret
    }

}
