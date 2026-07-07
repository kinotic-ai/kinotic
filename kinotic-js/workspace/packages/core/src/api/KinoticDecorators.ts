import { Kinotic } from '@/api/Kinotic'

import { ServiceIdentifier } from '@/api/ServiceIdentifier'
import { validateZone } from '@/api/ServiceZones'

/**
 * Decorator for registering services with the Kinotic ServiceRegistry.
 *
 * @author Navid Mitchell 🤝Grok
 * @since 3/25/2025
 */
const scopeFunctions = new WeakSet<Function>()
const versionRegistry = new WeakMap<Function, string>()
const zonesRegistry = new WeakMap<Function, string[]>()
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
 * Declares the zones a service is addressable in, relative to this client's trust context: the
 * declared zones are appended to {@link KinoticSingleton#zonePrefix}, so an application's service
 * can never leave its own `app.<organizationId>.<applicationId>` zone. A service declaring
 * multiple zones registers one address per zone. When absent, {@link KinoticSingleton#defaultZones}
 * (typically loaded from the project package.json `kinotic.zones` field) applies.
 * @param zones each zone is one or more dot separated labels of lowercase letters, digits, and
 *        interior dashes, e.g. `billing` or `billing.internal`
 */
export function Zones(zones: string[]) {
    for (const zone of zones) {
        validateZone(zone)
    }
    return function (value: Function, _context: ClassDecoratorContext<any>): void {
        zonesRegistry.set(value, zones)
    }
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

// Effective zones = zonePrefix . declaredZone. The prefix comes from the client's static
// configuration (never from the service itself), so a wrong declaration can only route nowhere,
// not into another application's zone — the gateway validates the prefix on every send/subscribe.
function resolveEffectiveZones(constructor: Function): (string | undefined)[] {
    const declaredZones = findInConstructorChain(zonesRegistry, constructor) ?? Kinotic.defaultZones ?? []
    const prefix = Kinotic.zonePrefix
    if (prefix != null && declaredZones.length > 0) {
        return declaredZones.map(zone => `${prefix}.${zone}`)
    } else if (prefix != null) {
        return [prefix]
    } else if (declaredZones.length > 0) {
        return [...declaredZones]
    }
    // No zone information at all: register at the un-zoned legacy address
    return [undefined]
}

/**
 * Registers each instance of the decorated class with the Kinotic ServiceRegistry.
 * The service name defaults to the class name; {@link Version}, {@link Scope}, and {@link Zones}
 * on the same class refine the registration.
 * @param namespace the optional namespace the service is published under
 * @param name the service name, defaults to the class name
 */
export function Publish(namespace?: string | null, name?: string) {
    return function <T extends new (...args: any[]) => object>(value: T, _context: ClassDecoratorContext<any>): T {
        return class extends value {
            constructor(...args: any[]) {
                super(...args)

                const version = findInConstructorChain(versionRegistry, this.constructor)
                const scope = resolveScope(this)

                for (const zone of resolveEffectiveZones(this.constructor)) {
                    if (zone !== undefined) {
                        validateZone(zone)
                    }
                    const serviceIdentifier = new ServiceIdentifier(namespace ?? null, name || value.name, zone)
                    if (version) {
                        serviceIdentifier.version = version
                    }
                    if (scope !== undefined) {
                        serviceIdentifier.scope = scope as string
                    }
                    Kinotic.serviceRegistry.register(serviceIdentifier, this)
                }
            }
        }
    }
}
