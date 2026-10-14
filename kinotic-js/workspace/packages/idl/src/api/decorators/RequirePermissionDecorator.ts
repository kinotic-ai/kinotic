import { C3Decorator } from './C3Decorator'

export class RequirePermissionDecorator extends C3Decorator {
    override readonly type = 'RequirePermission'
    permission = ''
    resourceType = 'entity_definition'
    idArgument = ''
    argumentIndex = -1
    label = ''
    tenantDelegable = false
}
