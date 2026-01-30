import {ArrayC3Type, StringC3Type} from '@kinotic/continuum-idl'

/**
 * Represents a {@link TenantSelection} type.
 * This is used to denote when a tenant selection is desired by a named query.
 */
export class TenantSelectionC3Type extends ArrayC3Type {

    public readonly type: string = 'tenantSelection'

    constructor() {
        super(new StringC3Type())
    }
}
