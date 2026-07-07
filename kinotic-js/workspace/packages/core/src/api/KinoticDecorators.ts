import { Kinotic } from '@/api/Kinotic'

import { ServiceIdentifier } from '@/api/ServiceIdentifier'

/**
 * Decorator for registering services with the Kinotic ServiceRegistry.
 *
 * @author Navid Mitchell 🤝Grok
 * @since 3/25/2025
 */
const scopeFunctions = new WeakSet<Function>()
const versionRegistry = new WeakMap<Function, string>()
const contextRegistry = new WeakMap<Function, number[]>()

// A Version above @Publish stamps the replacement class while one below it stamps the
// original, so the lookup walks the constructor prototype chain to find either.
function findInConstructorChain<T>(registry: WeakMap<Function, T>, constructor: Function): T | undefined {
    let current: Function | null = constructor
    while (current) {
        const value = registry.get(current)
        if (value !== undefined) {
            return value
        }
        current = Object.getPrototypeOf(current)
    }
    return undefined
}

// Scans prototype descriptors for the member marked @Scope; keyed by function identity,
// so the member's name is irrelevant and getters are not invoked while scanning.
function resolveScope(instance: object): unknown {
    let proto = Object.getPrototypeOf(instance)
    while (proto && proto !== Object.prototype) {
        for (const key of Object.getOwnPropertyNames(proto)) {
            if (key === 'constructor') {
                continue
            }
            const descriptor = Object.getOwnPropertyDescriptor(proto, key)
            if (descriptor?.get && scopeFunctions.has(descriptor.get)) {
                return descriptor.get.call(instance)
            }
            if (typeof descriptor?.value === 'function' && scopeFunctions.has(descriptor.value)) {
                return descriptor.value.call(instance)
            }
        }
        proto = Object.getPrototypeOf(proto)
    }
    return undefined
}

/**
 * Marks the getter or method that provides the service's scope. It is invoked on each
 * instance when the instance registers with the ServiceRegistry.
 */
export function Scope(value: Function, _context: ClassGetterDecoratorContext | ClassMethodDecoratorContext): void {
    scopeFunctions.add(value)
}

/**
 * Sets the semantic version a service registers under.
 * @param version the version in X.Y.Z[-optional] format
 */
export function Version(version: string) {
    if (!/^\d+\.\d+\.\d+(-[a-zA-Z0-9]+)?$/.test(version)) {
        throw new Error(`Invalid semantic version: ${version}. Must follow X.Y.Z[-optional] format.`)
    }
    return function (value: Function, _context: ClassDecoratorContext<any>): void {
        versionRegistry.set(value, version)
    }
}

/**
 * Marks which parameters of a service method receive the {@link ServiceContext} produced by the
 * registered {@link ContextInterceptor} instead of a caller-supplied argument.
 * @param parameterIndices zero-based indices of the context parameters
 */
export function Context(...parameterIndices: number[]) {
    // Keyed by the method function itself rather than context.addInitializer: Bun's TC39
    // decorator transform attaches initializers to the wrong class when a module declares
    // several decorated classes, and the function object needs no per-instance work.
    return function (value: Function, _context: ClassMethodDecoratorContext): void {
        contextRegistry.set(value, parameterIndices)
    }
}

/**
 * Returns the parameter indices marked with {@link Context} for the given method of a service
 * instance, or an empty array if none are marked.
 * @param serviceInstance the service instance to inspect
 * @param methodName the method to look up
 */
export function getContextParameterIndices(serviceInstance: object, methodName: string): number[] {
    const method = (serviceInstance as any)[methodName]
    return (typeof method === 'function' ? contextRegistry.get(method) : undefined) ?? []
}

/**
 * Registers each instance of the decorated class with the Kinotic ServiceRegistry under the
 * given namespace. The service name defaults to the class name; {@link Version} and
 * {@link Scope} on the same class refine the registration.
 * @param namespace the namespace the service is published under
 * @param name the service name, defaults to the class name
 */
export function Publish(namespace: string, name?: string) {
    return function <T extends new (...args: any[]) => object>(value: T, _context: ClassDecoratorContext<any>): T {
        return class extends value {
            constructor(...args: any[]) {
                super(...args)

                const serviceIdentifier = new ServiceIdentifier(namespace, name || value.name)

                const version = findInConstructorChain(versionRegistry, this.constructor)
                if (version) {
                    serviceIdentifier.version = version
                }

                const scope = resolveScope(this)
                if (scope !== undefined) {
                    serviceIdentifier.scope = scope as string
                }

                Kinotic.serviceRegistry.register(serviceIdentifier, this)
            }
        }
    }
}
