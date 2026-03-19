import {BaseConversionState} from '@/internal/converter/common/BaseConversionState'

export class StatementMapperConversionState extends BaseConversionState{

    public sourceName: string = 'entity'

    public targetName: string = 'ret'

    public indent: number = 4

    constructor(application: string) {
        super(application)
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
