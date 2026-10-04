import { Kinotic } from '@/api/Kinotic'

import type {AuthzCheckDeclaration, AuthzResourceDeclaration, FunctionContract, ServiceContract} from '@/api/ServiceContract'
import { ServiceIdentifier } from '@/api/ServiceIdentifier'
import { validateZone } from '@/api/ZoneUtil'

/**
 * Decorator for registering services with the Kinotic ServiceRegistry.
 *
 * @author Navid Mitchell 🤝Grok
 * @since 3/25/2025
 */
const scopeFunctions = new WeakSet<Function>()
const scopeOptionalFunctions = new WeakSet<Function>()
const versionRegistry = new WeakMap<Function, string>()
const zonesRegistry = new WeakMap<Function, string>()
const advertisedRegistry = new WeakMap<Function, boolean>()
const contextMarkedFunctions = new WeakSet<Function>()
const authzResourceRegistry = new WeakMap<Function, AuthzResourceDeclaration>()
const authzCheckRegistry = new WeakMap<Function, AuthzCheckDeclaration>()

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
 * Marks a method of a scoped published service that any instance may answer, because its
 * result does not depend on which instance executes it - typically a read of shared state
 * rather than of the instance's own.
 *
 * A scoped service normally listens only at its scoped address, so every invocation must name
 * an instance. When at least one method carries ScopeOptional, each instance also listens on
 * the service's shared unscoped address, where only the annotated methods may be invoked: an
 * unscoped invocation of any other method is rejected, since it would execute on whichever
 * instance happened to receive it.
 */
export function ScopeOptional(value: Function, _context: ClassMethodDecoratorContext): void {
    scopeOptionalFunctions.add(value)
}

// Scans prototype descriptors for methods marked @ScopeOptional; keyed by function identity
// like resolveScope, and descriptors are used so getters are not invoked while scanning.
export function scopeOptionalMethodNames(instance: object): Set<string> {
    const ret = new Set<string>()
    let proto = Object.getPrototypeOf(instance)
    while (proto && proto !== Object.prototype) {
        for (const key of Object.getOwnPropertyNames(proto)) {
            const descriptor = Object.getOwnPropertyDescriptor(proto, key)
            if (typeof descriptor?.value === 'function' && scopeOptionalFunctions.has(descriptor.value)) {
                ret.add(key)
            }
        }
        proto = Object.getPrototypeOf(proto)
    }
    return ret
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
 * can never leave its own `app.<organizationId>.<applicationId>` zone. When absent,
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
 * Declares the resource a service's functions act on, which puts the service's contract in the platform's
 * directory when its instance registers, so every request to it is checked. The check of each function is
 * derived by the platform from the function's name and parameters, as it is for the platform's own services:
 * a `find`, `get`, `count`, `search` or `list` function needs `can_view`, a `save`, `update` or `set` function
 * `can_edit`, a `delete` or `remove` function `can_delete`, and a `create` function `can_edit` on the parent,
 * each on the object a parameter named `id` or `<type>Id` names, or on the parent when none does. A function
 * the derivation cannot read declares its check with {@link AuthzCheck}.
 * @param declaration the resource type, or the type with its parent, object, permission and roles
 */
export function AuthzResource(declaration: AuthzResourceDeclaration | string) {
    const resource: AuthzResourceDeclaration = typeof declaration === 'string' ? {value: declaration} : declaration
    if (!resource.value) {
        throw new Error('AuthzResource must name the resource type')
    }
    return function (value: Function, _context: ClassDecoratorContext<any>): void {
        authzResourceRegistry.set(value, resource)
    }
}

/**
 * Declares the check of a function of an {@link AuthzResource} service: a permission of its own, such as
 * `can_generate`, another object than the derived one, the permissions it implies, a consistent answer, or
 * that any caller the zone admits may call it (`zoneOnly`).
 * @param declaration what the derivation cannot read from the function's name and parameters
 */
export function AuthzCheck(declaration: AuthzCheckDeclaration) {
    return function (value: Function, _context: ClassMethodDecoratorContext): void {
        authzCheckRegistry.set(value, declaration)
    }
}

/**
 * The contract of a service instance registered under the identifier, or null for a class declaring no
 * {@link AuthzResource}: its functions are the methods of its prototype chain, each with the names of the
 * parameters a request carries, read from the method's source, less the context a {@link Context} method
 * takes last. A runtime whose build renames parameters publishes names a check's template cannot reference,
 * which the platform refuses at registration.
 * @param serviceInstance the registered instance
 * @param serviceIdentifier how it is addressed, which the contract names
 */
export function serviceContractOf(serviceInstance: object, serviceIdentifier: ServiceIdentifier): ServiceContract | null {
    const resource = findInConstructorChain(authzResourceRegistry, serviceInstance.constructor)
    let ret: ServiceContract | null = null
    if (resource) {
        if (!serviceIdentifier.zone) {
            throw new Error(`${serviceIdentifier.qualifiedName()} declares a resource but no zone; a checked service is addressed in a zone`)
        }
        const functions: FunctionContract[] = []
        const seen = new Set<string>()
        // the same walk the invocation supervisor makes, so the contract carries exactly the functions served
        let proto = Object.getPrototypeOf(serviceInstance)
        while (proto && proto !== Object.prototype) {
            for (const key of Object.getOwnPropertyNames(proto)) {
                const descriptor = Object.getOwnPropertyDescriptor(proto, key)
                if (typeof descriptor?.value === 'function' && key !== 'constructor' && !seen.has(key)) {
                    seen.add(key)
                    const method: Function = descriptor.value
                    const parameters = parameterNames(method)
                    if (contextMarkedFunctions.has(method)) {
                        parameters.pop()
                    }
                    functions.push({name: key, parameters, check: authzCheckRegistry.get(method) ?? null})
                }
            }
            proto = Object.getPrototypeOf(proto)
        }
        ret = {
            namespace: serviceIdentifier.namespace,
            name: serviceIdentifier.name,
            version: serviceIdentifier.version ?? null,
            zone: serviceIdentifier.zone,
            resource,
            functions
        }
    }
    return ret
}

// The names of a function's parameters in order, from the parameter list of its source: a parameter with a
// default keeps its name, a rest parameter its name without the dots, and a destructured one, which has no
// name, is named for its position
function parameterNames(method: Function): string[] {
    const source = method.toString().replace(/\/\*[\s\S]*?\*\//g, '')
    let depth = 0
    let start = -1
    let end = -1
    for (let i = 0; i < source.length && end < 0; i++) {
        const c = source[i]
        if (c === '(') {
            if (depth === 0 && start < 0) {
                start = i + 1
            }
            depth++
        } else if (c === ')') {
            depth--
            if (depth === 0 && start >= 0) {
                end = i
            }
        }
    }
    const ret: string[] = []
    if (start >= 0 && end >= 0) {
        let nested = 0
        let current = ''
        const parts: string[] = []
        for (const c of source.substring(start, end)) {
            if ('([{'.includes(c)) {
                nested++
            } else if (')]}'.includes(c)) {
                nested--
            }
            if (c === ',' && nested === 0) {
                parts.push(current)
                current = ''
            } else {
                current += c
            }
        }
        parts.push(current)
        parts.map(part => (part.split('=')[0] ?? '').trim())
             .filter(part => part.length > 0)
             .forEach((part, index) => {
                 const name = part.replace(/^\.\.\./, '')
                 ret.push(/^[A-Za-z_$][\w$]*$/.test(name) ? name : `arg${index}`)
             })
    }
    return ret
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
