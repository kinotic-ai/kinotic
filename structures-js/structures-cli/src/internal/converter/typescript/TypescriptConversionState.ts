import {UtilFunctionLocator} from '../../UtilFunctionLocator.js'
import {BaseConversionState} from '../common/BaseConversionState.js'

/**
 * The state of the Typescript to C3Type conversion process.
 */
export class TypescriptConversionState extends BaseConversionState{

    /**
     * Contains a stack of object names for objects being processed.
     */
    public objectNameStack: string[] = []
    /**
     * Contains a stack of property names for any properties processed that are a union type.
     */
    public unionPropertyNameStack: string[] = []
    /**
     * Boolean to indicate if the source path should be added to the metadata for the {@link C3Type}s created.
     */
    public shouldAddSourcePathToMetadata: boolean = true

    /**
     * Boolean to indicate if multi-tenant selection is enabled.
     * This is done by adding a {@link TenantId} decorator to the entity.
     */
    public multiTenantSelectionEnabled: boolean = false

    constructor(application: string,
                utilFunctionLocator: UtilFunctionLocator | null) {
        super(application, utilFunctionLocator)
    }

}
