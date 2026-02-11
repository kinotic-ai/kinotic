import {EntityServiceDecorator} from '@/api/idl/decorators/EntityServiceDecorator'

export class PolicyDecorator extends EntityServiceDecorator {

    public policies: string[][]

    constructor(policies: string[][]) {
        super()
        this.type = 'PolicyDecorator'
        this.policies = policies
    }
}

/**
 * Provides a mechanism to apply the @Policy decorator to an {@link EntityService}
 * @param policies to be supplied
 */
// @ts-ignore
export function $Policy(policies: string[][]): PolicyDecorator{
    return new PolicyDecorator(policies)
}
