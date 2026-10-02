import { EntityServiceDecorator } from './EntityServiceDecorator'

export class AbacPolicyDecorator extends EntityServiceDecorator {

    public expression: string

    constructor(expression: string) {
        super()
        this.type = 'AbacPolicyDecorator'
        this.expression = expression
    }
}

/**
 * Provides a mechanism to apply an ABAC policy expression to an {@link EntityService}
 * @param expression the ABAC policy expression string
 */
export function $AbacPolicy(expression: string): AbacPolicyDecorator {
    return new AbacPolicyDecorator(expression)
}
