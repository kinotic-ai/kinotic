import 'reflect-metadata'
import { Kinotic } from '@/api/Kinotic'

import { ServiceIdentifier } from '@/api/ServiceIdentifier'

/**
 * Decorator for registering services with the Kinotic ServiceRegistry.
 *
 * @author Navid Mitchell 🤝Grok
 * @since 3/25/2025
 */
const SCOPE_METADATA_KEY = Symbol('scope')
const VERSION_METADATA_KEY = Symbol('version')
export const CONTEXT_METADATA_KEY: unique symbol = Symbol('context')
export const ABAC_POLICY_METADATA_KEY: unique symbol = Symbol('abacPolicy')

//@ts-ignore
export function Scope(target: any, propertyKey: string, descriptor?: PropertyDescriptor): void {
    Reflect.defineMetadata(SCOPE_METADATA_KEY, propertyKey, target)
}

export function Version(version: string) {
    if (!/^\d+\.\d+\.\d+(-[a-zA-Z0-9]+)?$/.test(version)) {
        throw new Error(`Invalid semantic version: ${version}. Must follow X.Y.Z[-optional] format.`)
    }
    return function (target: Function): void {
        Reflect.defineMetadata(VERSION_METADATA_KEY, version, target)
    }
}

export function Context() {
    return function (target: any, propertyKey: string, parameterIndex: number): void {
        const existingContexts = Reflect.getMetadata(CONTEXT_METADATA_KEY, target, propertyKey) || [];
        existingContexts.push(parameterIndex);
        Reflect.defineMetadata(CONTEXT_METADATA_KEY, existingContexts, target, propertyKey);
    }
}

/**
 * Decorator that attaches an ABAC policy expression to a published service method.
 * Use boolean logic (and, or, not) within the expression to combine conditions.
 *
 * @param expression the ABAC policy expression string
 */
export function AbacPolicy(expression: string) {
    return function (target: any, propertyKey: string, descriptor: PropertyDescriptor): PropertyDescriptor {
        Reflect.defineMetadata(ABAC_POLICY_METADATA_KEY, expression, target, propertyKey)
        return descriptor
    }
}

export function Publish(namespace: string, name?: string) {
    return function (target: Function) {
        const original = target
        const serviceIdentifier = new ServiceIdentifier(namespace, name || target.name)

        const version = Reflect.getMetadata(VERSION_METADATA_KEY, target)
        if (version) {
            serviceIdentifier.version = version
        }

        const newConstructor: any = function (this: any, ...args: any[]) {
            const instance = Reflect.construct(original, args)

            const scopeProperty = Reflect.getMetadata(SCOPE_METADATA_KEY, target.prototype)
            if (scopeProperty) {
                const scopeValue = instance[scopeProperty]
                serviceIdentifier.scope = typeof scopeValue === 'function' ? scopeValue.call(instance) : scopeValue
            }

            // Register with the default Kinotic's ServiceRegistry
            Kinotic.serviceRegistry.register(serviceIdentifier, instance)

            return instance
        }

        newConstructor.prototype = original.prototype
        return newConstructor as any
    }
}
