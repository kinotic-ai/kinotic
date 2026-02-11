import {EntityServiceDecorator} from '@/api/idl/decorators/EntityServiceDecorator'

export class RoleDecorator extends EntityServiceDecorator {

    public roles: string[]

    constructor(roles: string[]) {
        super()
        this.type = 'RoleDecorator'
        this.roles = roles
    }
}

/**
 * Provides a mechanism to apply the @Role decorator to an {@link EntityService}
 * @param roles to be supplied
 */
// @ts-ignore
export function $Role(roles: string[]): RoleDecorator{
    return new RoleDecorator(roles)
}
