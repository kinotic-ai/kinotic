import { Kinotic } from '@/api/Kinotic'

import { ServiceIdentifier } from '@/api/ServiceIdentifier'
import { validateZone } from '@/api/ZoneUtil'
import type { AuthzCheckDeclaration } from '@/api/AuthzCheckDeclaration'
import type { AuthzResourceDeclaration } from '@/api/AuthzResourceDeclaration'
import { AuthzCheckDecorator, AuthzResourceDecorator, AuthzRoleDeclaration } from '@kinotic-ai/idl'

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
const authzResourceDeclarations = new WeakMap<Function, AuthzResourceDeclaration>()
const authzUncheckedClasses = new WeakSet<Function>()
// keyed by the method function itself, as Context is
const authzCheckedFunctions = new WeakMap<Function, AuthzCheckDeclaration>()
const authzUncheckedFunctions = new WeakSet<Function>()

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
 * Marks a published service as acting on one type of resource, which `kinotic sync` reads from the class's
 * source into the service's definition, which the runtime registers in the system's service directory when
 * the service comes online, so every request to the service is authorized before the service sees it. The type
 * and the permissions its functions need become part of the application's authorization model, where roles
 * bundle permissions and grants bind roles to callers.
 *
 * A function's check has three parts: the permission it needs, the type of the resource it is checked on, and
 * the id of that resource. Each part is derived from the function's name and parameters where it can be;
 * {@link AuthzCheck} states a part derivation gets wrong or cannot know, and {@link AuthzUnchecked} marks a
 * function served with no check. A function left with no check and no marker fails the service's registration.
 *
 * The permission is derived from the first word of the function's name: `find`, `get`, `count`, `search` and
 * `list` need `can_read`; `save`, `update` and `set` need `can_edit`; `delete` and `remove` need `can_delete`;
 * `create` needs `can_edit` of this type on the parent. The resource's id is the parameter named `id` or
 * `<type>Id` (`reportId` for the type `report`), else the `id` of the first object parameter; a function with
 * neither is checked on the parent, whose id is found the same way with the parent's name, else on the caller's
 * own scope.
 *
 * Example: with `@AuthzResource({value: 'report', parent: 'tenant'})`, `findById(id: string)` is checked for
 * `report_can_read` on the report `id` names, `save(report: Report)` for `report_can_edit` on the report
 * `report.id` names, and `findReports()` for `report_can_read` on the caller's tenant, with nothing declared on
 * them. A runtime holding no definition for a service declaring a resource refuses to serve it.
 * @param declaration the resource type, or the type with its parent, resource id, permission and roles
 */
export function AuthzResource(declaration: AuthzResourceDeclaration | string) {
    return function (value: Function, _context: ClassDecoratorContext<any>): void {
        authzResourceDeclarations.set(value, typeof declaration === 'string' ? {value: declaration} : declaration)
    }
}

/**
 * States a part of one function's check that derivation gets wrong or cannot know, which `kinotic sync` reads
 * into the service's definition: the permission, the type of the resource, or its id. Every part left out keeps
 * the derived value, or the one the service declares with {@link AuthzResource}, so a function states only the
 * part derivation misses. A function served with no check is marked {@link AuthzUnchecked} instead.
 *
 * Example: `@AuthzCheck({permission: 'can_report', resourceId: '{registration.id}'})` on
 * `heartbeat(registration: Registration)`, whose name derives no permission and whose id is inside the body.
 * @param declaration the parts of the check derivation cannot read from the function's name and parameters
 */
export function AuthzCheck(declaration: AuthzCheckDeclaration) {
    return function (value: Function, _context: ClassMethodDecoratorContext): void {
        authzCheckedFunctions.set(value, declaration)
    }
}

/**
 * Marks a function of an {@link AuthzResource} service, or a whole class declaring no resource, as served with
 * no authorization check, which `kinotic sync` reads into the service's definition: any caller the service's
 * zone admits may call it, and the service itself is responsible for answering only what the caller may see or
 * do. It is for a function that has no resource to check, such as a listing of the caller's own grants, and for a
 * service whose every function answers for the caller alone. The mark is the only way a function is served
 * unchecked: a function the definition neither checks nor marks is refused. A function carrying this and
 * {@link AuthzCheck} fails the service's registration, as does a class carrying this and {@link AuthzResource}.
 */
export function AuthzUnchecked(value: Function, context: ClassDecoratorContext<any> | ClassMethodDecoratorContext): void {
    if (context.kind === 'class') {
        authzUncheckedClasses.add(value)
    } else {
        authzUncheckedFunctions.add(value)
    }
}

/**
 * Returns whether the given service instance's class, or a class it extends, declares an {@link AuthzResource} or
 * is marked {@link AuthzUnchecked}: either way the service's definition carries what the system serves it by.
 * @param serviceInstance the service instance to inspect
 */
export function declaresAuthz(serviceInstance: object): boolean {
    let ret = false
    let current: Function | null = serviceInstance.constructor
    while (current && !ret) {
        ret = authzResourceDeclarations.has(current) || authzUncheckedClasses.has(current)
        current = Object.getPrototypeOf(current)
    }
    return ret
}

/**
 * The authorization decorators `kinotic sync` writes into a service's definition, as the service instance declares
 * them: the {@link AuthzResource} of its class or of the nearest class it extends, and for each named function the
 * {@link AuthzCheck} or {@link AuthzUnchecked} of the function the instance serves under that name, a class or
 * class it extends marked unchecked marking every function. A function declaring neither maps to null.
 * @param serviceInstance the service instance to inspect
 * @param functionNames the functions the instance serves
 */
export function authzDecoratorsOf(serviceInstance: object, functionNames: string[]): {
    resource: AuthzResourceDecorator | null
    checks: Map<string, AuthzCheckDecorator | null>
} {
    const declared = findInConstructorChain(authzResourceDeclarations, serviceInstance.constructor)
    let resource: AuthzResourceDecorator | null = null
    if (declared) {
        resource = new AuthzResourceDecorator(declared.value)
        resource.parent = declared.parent ?? null
        resource.resourceId = declared.resourceId ?? null
        resource.permission = declared.permission ?? null
        resource.roles = (declared.roles ?? []).map(role => new AuthzRoleDeclaration(role.id, role.permissions ?? []))
    }
    let uncheckedClass = false
    let current: Function | null = serviceInstance.constructor
    while (current && !uncheckedClass) {
        uncheckedClass = authzUncheckedClasses.has(current)
        current = Object.getPrototypeOf(current)
    }
    const checks = new Map<string, AuthzCheckDecorator | null>()
    for (const name of functionNames) {
        const method = (serviceInstance as any)[name]
        const check = typeof method === 'function' ? authzCheckedFunctions.get(method) : undefined
        let decorator: AuthzCheckDecorator | null = null
        if (check) {
            decorator = new AuthzCheckDecorator()
            decorator.permission = check.permission ?? null
            decorator.resource = check.resource ?? null
            decorator.resourceId = check.resourceId ?? null
            decorator.implies = check.implies ?? []
            decorator.consistent = check.consistent === true
        } else if (uncheckedClass || (typeof method === 'function' && authzUncheckedFunctions.has(method))) {
            decorator = new AuthzCheckDecorator()
            decorator.unchecked = true
        }
        checks.set(name, decorator)
    }
    return { resource, checks }
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
