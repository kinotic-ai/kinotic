import {AbstractDefinition} from '@/api/AbstractDefinition'
import { C3Type } from '@/api/C3Type'
import {C3Decorator} from '@/api/decorators/C3Decorator'
import {MetadataType} from '@/api/HasMetadata'

/**
 * Defines a property for a {@link ObjectC3Type}
 * Created by Navíd Mitchell 🤪 on 2/22/24.
 */
export class PropertyDefinition extends AbstractDefinition {

    /**
     * This is the {@link C3Type} of this {@link PropertyDefinition}
     */
    public type: C3Type;

    constructor(name: string,
                type: C3Type,
                decorators?: C3Decorator[] | null,
                metadata?: MetadataType | null) {
        super(name, decorators, metadata)
        this.type = type
    }
}
