import {C3Type, ObjectC3Type} from '@kinotic/continuum-idl'

/**
 * This is a placeholder for the Page type.
 * This is not a complete type but a placeholder for the server to know what to expect.
 */
export class PageC3Type extends C3Type {

    /**
     * The {@link ObjectC3Type} of the content of the page
     * The actual page content will be a list of this type
     */
    public contentType: ObjectC3Type

    constructor(contentType: ObjectC3Type) {
        super('page')
        this.contentType = contentType
    }
}
