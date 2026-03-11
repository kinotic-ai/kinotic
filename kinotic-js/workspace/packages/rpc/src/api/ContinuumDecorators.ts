/*
 * Copyright 2008-2021 Kinotic and the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the 'License')
 * See https://www.apache.org/licenses/LICENSE-2.0
 */

import 'reflect-metadata'
import { Continuum } from '@/api/Continuum.js'
import { ServiceIdentifier } from '@/core/api/ServiceIdentifier.js'

/**
 * Decorator for registering services with the Continuum ServiceRegistry.
 *
 * @author Navid Mitchell 🤝Grok
 * @since 3/25/2025
 */
const SCOPE_METADATA_KEY = Symbol('scope')
const VERSION_METADATA_KEY = Symbol('version')
export const CONTEXT_METADATA_KEY = Symbol('context')

//@ts-ignore
export function Scope(target: any, propertyKey: string, descriptor?: PropertyDescriptor) {
    Reflect.defineMetadata(SCOPE_METADATA_KEY, propertyKey, target)
}

export function Version(version: string) {
    if (!/^\d+\.\d+\.\d+(-[a-zA-Z0-9]+)?$/.test(version)) {
        throw new Error(`Invalid semantic version: ${version}. Must follow X.Y.Z[-optional] format.`)
    }
    return function (target: Function) {
        Reflect.defineMetadata(VERSION_METADATA_KEY, version, target)
    }
}

export function Context() {
    return function (target: any, propertyKey: string, parameterIndex: number) {
        const existingContexts = Reflect.getMetadata(CONTEXT_METADATA_KEY, target, propertyKey) || [];
        existingContexts.push(parameterIndex);
        Reflect.defineMetadata(CONTEXT_METADATA_KEY, existingContexts, target, propertyKey);
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

            // Register with the singleton Continuum's ServiceRegistry
            Continuum.serviceRegistry.register(serviceIdentifier, instance)

            return instance
        }

        newConstructor.prototype = original.prototype
        return newConstructor as any
    }
}
