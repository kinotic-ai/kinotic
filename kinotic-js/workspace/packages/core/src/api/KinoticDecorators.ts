import { Kinotic } from '@/api/Kinotic'

import { ServiceIdentifier } from '@/api/ServiceIdentifier'
import { validateZone } from '@/api/ZoneUtil'

/**
 * Decorator for registering services with the Kinotic ServiceRegistry.
 *
 * @author Navid Mitchell 🤝Grok
 * @since 3/25/2025
 */
const scopeFunctions = new WeakSet<Function>()
const versionRegistry = new WeakMap<Function, string>()
const zonesRegistry = new WeakMap<Function, string>()
const advertisedRegistry = new WeakMap<Function, boolean>()
const contextMarkedFunctions = new WeakSet<Function>()

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
 * Marks the getter or method that provides the service's scope, which targets requests at one
 * specific instance of the service, such as the copy running on a particular node. It is
 * invoked on each instance when the instance registers with the ServiceRegistry.
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
 * Declares the zone a service is addressable in, relative to this client's trust context: the
 * declared zone is appended to {@link KinoticSingleton#zonePrefix}, so an application's service
 * can never leave its own `kinotic-app.<organizationId>.<applicationId>` zone. When absent,
 * {@link KinoticSingleton#defaultZone} (typically loaded from the project package.json
 * `kinotic.zone` field) applies.
 * @param zone one or more dot separated labels of lowercase letters, digits, and interior
 *        dashes, e.g. `billing` or `billing.internal`
 */
export function Zone(zone: string) {
    validateZone(zone)
    return function (value: Function, _context: ClassDecoratorContext<any>): void {
        zonesRegistry.set(value, zone)
    }
}

/**
 * Returns whether the given service instance's class was published with `advertise` set.
 * @param serviceInstance the service instance to inspect
 */
export function isAdvertised(serviceInstance: object): boolean {
    return findInConstructorChain(advertisedRegistry, serviceInstance.constructor) === true
}

/**
 * Marks a service method that receives the {@link ServiceContext} produced by the registered
 * {@link ContextInterceptor}. The context parameter MUST be the method's final parameter:
 * callers do not pass it, and the platform appends it after the caller-supplied arguments.
 */
export function Context(value: Function, _context: ClassMethodDecoratorContext): void {
    // Keyed by the method function itself, like Scope, so Bun's decorator-context bugs
    // cannot affect it.
    contextMarkedFunctions.add(value)
}

/**
 * Returns whether the given method of a service instance is marked with {@link Context}.
 * @param serviceInstance the service instance to inspect
 * @param methodName the method to look up
 */
export function receivesContext(serviceInstance: object, methodName: string): boolean {
    const method = (serviceInstance as any)[methodName]
    return typeof method === 'function' && contextMarkedFunctions.has(method)
}

// Effective zone = zonePrefix . declaredZone. The prefix comes from the client's static
// configuration (never from the service itself), so a wrong declaration can only route nowhere,
// not into another application's zone — the gateway validates the prefix on every send/subscribe.
// A null result means the service registers at its un-zoned legacy address.
function resolveEffectiveZone(constructor: Function): string | null {
    const declaredZone = findInConstructorChain(zonesRegistry, constructor) ?? Kinotic.defaultZone
    const prefix = Kinotic.zonePrefix
    let effectiveZone: string | null
    if (prefix != null && declaredZone != null) {
        effectiveZone = `${prefix}.${declaredZone}`
    } else if (prefix != null) {
        effectiveZone = prefix
    } else {
        effectiveZone = declaredZone ?? null
    }
    if (effectiveZone != null) {
        validateZone(effectiveZone)
    }
    return effectiveZone
}

/**
 * Registers each instance of the decorated class with the Kinotic ServiceRegistry.
 * The service name defaults to the class name; {@link Version}, {@link Scope}, and {@link Zone}
 * on the same class refine the registration.
 * @param namespace the optional namespace the service is published under
 * @param name the service name, defaults to the class name
 * @param advertise when true the service advertises itself in the platform ServiceDirectory,
 *        so it appears in directory listings
 */
export function Publish(namespace?: string | null, name?: string, advertise: boolean = false) {
    return function <T extends new (...args: any[]) => object>(value: T, _context: ClassDecoratorContext<any>): T {
        advertisedRegistry.set(value, advertise)
        return class extends value {
            constructor(...args: any[]) {
                super(...args)

                const zone = resolveEffectiveZone(this.constructor)
                const serviceIdentifier = new ServiceIdentifier(namespace ?? null,
                                                                name || value.name,
                                                                zone ?? undefined)

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
