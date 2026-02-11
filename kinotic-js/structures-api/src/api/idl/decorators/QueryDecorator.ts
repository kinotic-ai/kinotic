import {C3Decorator} from '@kinotic/continuum-idl'

export class QueryDecorator extends C3Decorator {

    public statements: string

    constructor(statements: string) {
        super()
        this.type = 'Query'
        this.statements = statements
    }
}
