/** Capability metadata compiled by kinotic sync. Grants are administered in the UI. */
export interface PermissionOptions {
    label?: string
    tenantDelegable?: boolean
    resourceType?: string
}

export interface ResourceTargetOptions {
    type?: string
    idArgument?: string
}

export function RequirePermission(permission: string = '', options: PermissionOptions = {}) {
    return (_value: unknown, _context: ClassDecoratorContext | ClassMethodDecoratorContext | ClassFieldDecoratorContext): void => {
        if (permission && !/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)*$/.test(permission)) throw new Error('Invalid permission name')
        if (options.resourceType && !/^[A-Za-z][A-Za-z0-9_]*$/.test(options.resourceType)) throw new Error('Invalid resource type')
    }
}

export function PermissionNamespace(namespace: string) {
    return (_value: Function, _context: ClassDecoratorContext): void => {
        if (!/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)*$/.test(namespace)) throw new Error('Invalid permission namespace')
    }
}

export function ResourceTarget(options: ResourceTargetOptions) {
    return (_value: unknown, _context: ClassDecoratorContext | ClassMethodDecoratorContext): void => {
        if (options.idArgument && !/^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*$/.test(options.idArgument)) throw new Error('Invalid resource argument')
    }
}
