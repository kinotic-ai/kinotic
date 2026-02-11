import {UtilFunctionLocator} from '../../UtilFunctionLocator.js'
import {BaseConversionState} from '../common/BaseConversionState.js'

export class StatementMapperConversionState extends BaseConversionState{

    public sourceName: string = 'entity'

    public targetName: string = 'ret'

    public indent: number = 4

    constructor(namespace: string, utilFunctionLocator: UtilFunctionLocator| null) {
        super(namespace, utilFunctionLocator, true)
    }

    public indentMore(): StatementMapperConversionState {
        this.indent += 2
        return this
    }

    public indentLess(): StatementMapperConversionState {
        this.indent -= 2
        return this
    }
}
