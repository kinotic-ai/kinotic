import {ComplexC3Type} from '@/api/ComplexC3Type'
import {C3Decorator} from '@/api/decorators/C3Decorator'
import {MetadataType} from '@/api/HasMetadata'

/**
 * Created by Navíd Mitchell 🤪 on 4/13/23.
 */
export class EnumC3Type extends ComplexC3Type {

    /**
     * The values that are part of this enum
     */
    public values: string[] = []

    constructor(name: string,
                namespace: string,
                decorators?: C3Decorator[] | null,
                metadata?: MetadataType | null) {
        super('enum', name, namespace, decorators, metadata)
    }

    public addValue(value: string): EnumC3Type {
        this.values.push(value)
        return this
    }
}
