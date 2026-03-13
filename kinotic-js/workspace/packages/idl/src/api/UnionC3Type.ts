import {ComplexC3Type} from '@/api/ComplexC3Type'
import {C3Decorator} from '@/api/decorators/C3Decorator'
import {MetadataType} from '@/api/HasMetadata'
import {ObjectC3Type} from "@/api/ObjectC3Type"

/**
 * Represents a union type in the IDL.
 * Union types are a way to represent a type that can be one of many types.
 * Created by Navíd Mitchell 🤪 on 4/13/23.
 */
export class UnionC3Type extends ComplexC3Type {

    /**
     * The types that are part of this union
     * All types must have a field with the name of the discriminator
     */
    public types: ObjectC3Type[] = []

    constructor(name: string,
                namespace: string,
                decorators?: C3Decorator[] | null,
                metadata?: MetadataType | null) {
        super('union', name, namespace, decorators, metadata)
    }
}
