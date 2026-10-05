import debug from 'debug'
import { ServiceIdentifier } from './ServiceIdentifier'
import { ServiceInvocationSupervisor } from '@/internal/api/ServiceInvocationSupervisor'
import opentelemetry, { SpanKind, SpanStatusCode, type Tracer } from '@opentelemetry/api'
import {
    ATTR_SERVER_ADDRESS,
    ATTR_SERVER_PORT,
} from '@opentelemetry/semantic-conventions'
import { Observable } from 'rxjs'
import { first, map } from 'rxjs/operators'
import info from '../../package.json' with { type: 'json' }
import { Event } from './event/EventBus'
import { EventConstants, type IEvent, type IEventBus } from './event/IEventBus'
import type {IEventFactory, IServiceProxy, IServiceRegistry} from './IServiceRegistry'
import type {ContextInterceptor, ServiceContext} from './ContextInterceptor'
import type { ServiceDefinition } from '@kinotic-ai/idl'
import { declaresAuthzResource, isAdvertised } from './KinoticDecorators'
import type { ServiceDirectoryEntry } from './ServiceDirectoryEntry'

/**
 * An implementation of a {@link IEventFactory} which uses JSON content
 */
export class JsonEventFactory implements IEventFactory {
    create(cri: string, args: any[] | null | undefined): IEvent {
        const event: Event = new Event(cri)
        event.setHeader(EventConstants.CONTENT_TYPE_HEADER, EventConstants.CONTENT_JSON)
        if (args != null) {
            event.setDataString(JSON.stringify(args))
        }
        return event
    }
}

/**
 * An implementation of a {@link IEventFactory} which uses text content
 */
export class TextEventFactory implements IEventFactory {
    create(cri: string, args: any[] | null | undefined): IEvent {
        const event: Event = new Event(cri)
        event.setHeader(EventConstants.CONTENT_TYPE_HEADER, EventConstants.CONTENT_TEXT)
        if (args != null) {
            let data: string = ''
            let i = 0
            for (const arg of args) {
                if (i > 0) {
                    data = data + '\n'
                }
                data = data + arg
                i++
            }
            if (data.length > 0) {
                event.setDataString(data)
            }
        }
        return event
    }
}

/**
 * The platform service a runtime registers its application services with, in the zone an organization's
 * runtimes reach.
 */
const SERVICE_DIRECTORY_SERVICE = 'app-api~org.kinotic.app.api.services.ServiceDirectoryService'
// an application's zone is app.<organizationId>.<applicationId>, with the service's own labels after it
const APPLICATION_ZONE = /^app\.[^.]+\.([^.]+)(\.|$)/

// the definitions declared for the project's published services, by qualified name
const declaredDefinitions = new Map<string, ServiceDefinition>()

/**
 * Declares the definitions of the project's published services, as `kinotic sync` generates them into the
 * project's ServiceDefinitions module, so every registry registers each service in the platform's service
 * directory with its definition when the service comes online. A definition declared again for a qualified
 * name replaces the one held.
 * @param definitions the generated definitions
 */
export function declareServiceDefinitions(definitions: ServiceDefinition[]): void {
    for (const definition of definitions) {
        declaredDefinitions.set(qualifiedNameOf(definition.namespace, definition.name), definition)
    }
}

// The namespace and name joined with '.', omitting an absent namespace, as the identifier qualifies them
function qualifiedNameOf(namespace: string | null | undefined, name: string): string {
    return namespace ? `${namespace}.${name}` : name
}

// The names of the methods an instance serves: the same prototype walk the invocation supervisor makes
function servedFunctions(serviceInstance: object): string[] {
    const ret = new Set<string>()
    let proto = Object.getPrototypeOf(serviceInstance)
    while (proto && proto !== Object.prototype) {
        for (const key of Object.getOwnPropertyNames(proto)) {
            const descriptor = Object.getOwnPropertyDescriptor(proto, key)
            if (typeof descriptor?.value === 'function' && key !== 'constructor') {
                ret.add(key)
            }
        }
        proto = Object.getPrototypeOf(proto)
    }
    return [...ret].sort()
}

/**
 * The directory entry of a service registered in an application's zone: the definition the project declared
 * for it, which names exactly the functions the instance serves, with what the registration adds. Null for a
 * service in no application's zone, or one the project declared no definition for, which serves on its zone
 * alone unless it declares a resource.
 * @throws when the definition declares other functions than the instance serves, or the service declares a
 *         resource and the project declares no definition for it
 */
function directoryEntryOf(serviceInstance: object, serviceIdentifier: ServiceIdentifier): ServiceDirectoryEntry | null {
    let ret: ServiceDirectoryEntry | null = null
    const application = serviceIdentifier.zone ? APPLICATION_ZONE.exec(serviceIdentifier.zone) : null
    if (application) {
        const qualifiedName = qualifiedNameOf(serviceIdentifier.namespace, serviceIdentifier.name)
        const definition = declaredDefinitions.get(qualifiedName)
        if (definition) {
            const declared = definition.functions.map(f => f.name).sort()
            const served = servedFunctions(serviceInstance)
            if (declared.join() !== served.join()) {
                throw new Error(`${qualifiedName} serves the functions [${served}] but its definition declares [${declared}];`
                                + ' run kinotic sync and import the generated ServiceDefinitions module')
            }
            ret = {
                // the group is what the zone matched on
                applicationId: application[1] as string,
                zone: serviceIdentifier.zone as string,
                version: serviceIdentifier.version ?? null,
                advertised: isAdvertised(serviceInstance),
                serviceDefinition: definition
            }
        } else if (declaresAuthzResource(serviceInstance)) {
            throw new Error(`${qualifiedName} declares a resource but the project declares no definition for it;`
                            + ' run kinotic sync and import the generated ServiceDefinitions module')
        }
    }
    return ret
}

/**
 * The default implementation of {@link IServiceRegistry}
 */
export class ServiceRegistry implements IServiceRegistry {
    private _eventBus: IEventBus
    private supervisors: Map<string, ServiceInvocationSupervisor> = new Map()
    // the directory entries of the application services registered, by CRI, registered once the connection is up
    private entries: Map<string, ServiceDirectoryEntry> = new Map()
    private contextInterceptor: ContextInterceptor<any> | null = null
    private debugLogger = debug('kinotic:serviceRegistry')

    constructor(eventBus: IEventBus) {
        this._eventBus = eventBus
    }

    public set eventBus(eventBus: IEventBus) {
        this._eventBus = eventBus
        // update all supervisors to use the new event bus
        for (const supervisor of this.supervisors.values()) {
            supervisor.eventBus = eventBus
        }
    }

    public get eventBus(): IEventBus {
        return this._eventBus
    }

    public serviceProxy(serviceIdentifier: string): IServiceProxy {
        return new ServiceProxy(serviceIdentifier, this)
    }

    public register(serviceIdentifier: ServiceIdentifier, service: any): void {
        const criString = serviceIdentifier.cri().raw()
        if (!this.supervisors.has(criString)) {
            this.debugLogger(`Registering service for CRI: ${criString}`)
            // resolved before the supervisor starts, so a service whose entry cannot be built never serves
            const entry = directoryEntryOf(service, serviceIdentifier)
            const supervisor = new ServiceInvocationSupervisor(
                serviceIdentifier,
                service,
                this.eventBus,
                () => this.contextInterceptor
            )
            this.supervisors.set(criString, supervisor)
            supervisor.start()
            if (entry) {
                this.entries.set(criString, entry)
                if (this.eventBus.isConnected()) {
                    this.registerEntry(entry).catch(error => this.debugLogger(`Failed to register ${criString} in the directory`, error))
                }
            }
        }
    }

    public unRegister(serviceIdentifier: ServiceIdentifier): void {
        const criString = serviceIdentifier.cri().raw()
        const supervisor = this.supervisors.get(criString)
        if (supervisor) {
            this.debugLogger(`Unregistering service for CRI: ${criString}`)
            supervisor.stop()
            this.supervisors.delete(criString)
            this.entries.delete(criString)
        }
    }

    /**
     * Registers every application service registered so far in the platform's service directory, with the
     * definition the project declared for it, so the directory lists the service and the platform checks
     * requests to it; a service the platform refuses fails the registration. A service registered later is
     * registered in the directory as it registers here.
     */
    public async registerEntries(): Promise<void> {
        for (const entry of this.entries.values()) {
            await this.registerEntry(entry)
        }
    }

    private async registerEntry(entry: ServiceDirectoryEntry): Promise<void> {
        this.debugLogger(`Registering ${entry.zone}~${qualifiedNameOf(entry.serviceDefinition.namespace, entry.serviceDefinition.name)} in the directory`)
        await this.serviceProxy(SERVICE_DIRECTORY_SERVICE).invoke('register', [entry])
    }

    public registerContextInterceptor<T extends ServiceContext>(interceptor: ContextInterceptor<T> | null): void {
        this.contextInterceptor = interceptor
    }
}

/**
 * The default implementation of {@link IEventFactory} which uses JSON content
 */
const defaultEventFactory: IEventFactory = new JsonEventFactory()

/**
 * For internal use only should not be instantiated directly
 */
class ServiceProxy implements IServiceProxy {
    public readonly serviceIdentifier: string
    private readonly serviceRegistry: ServiceRegistry
    private tracer: Tracer

    constructor(serviceIdentifier: string, serviceRegistry: ServiceRegistry) {
        if (typeof serviceIdentifier === 'undefined' || serviceIdentifier.length === 0) {
            throw new Error('The serviceIdentifier provided must contain a value')
        }
        this.serviceIdentifier = serviceIdentifier
        this.serviceRegistry = serviceRegistry
        this.tracer = opentelemetry.trace.getTracer(info.name, info.version)
    }

    invoke(methodIdentifier: string,
           args?: any[] | null | undefined,
           scope?: string | null | undefined,
           eventFactory?: IEventFactory | null | undefined): Promise<any> {
        return this.tracer.startActiveSpan(
            `${this.serviceIdentifier}/${methodIdentifier}`,
            {
                kind: SpanKind.CLIENT
            },
            async (span) => {
                if (scope) {
                    span.setAttribute('kinotic.scope', scope)
                }
                span.setAttribute('rpc.system', 'kinotic')
                span.setAttribute('rpc.service', this.serviceIdentifier)
                span.setAttribute('rpc.method', methodIdentifier)

                return this.__invokeStream(false, methodIdentifier, args, scope, eventFactory)
                           .pipe(first())
                           .toPromise()
                           .then(
                               async (value) => {
                                   span.end()
                                   return value
                               },
                               async (ex) => {
                                   span.recordException(ex)
                                   span.setStatus({ code: SpanStatusCode.ERROR })
                                   span.end()
                                   throw ex
                               })
            })
    }

    invokeStream(methodIdentifier: string,
                 args?: any[] | null | undefined,
                 scope?: string | null | undefined,
                 eventFactory?: IEventFactory | null | undefined): Observable<any> {
        return this.__invokeStream(true, methodIdentifier, args, scope, eventFactory)
    }

    private __invokeStream(sendControlEvents: boolean,
                           methodIdentifier: string,
                           args?: any[] | null | undefined,
                           scope?: string | null | undefined,
                           eventFactory?: IEventFactory | null | undefined): Observable<any> {
        const cri: string = EventConstants.SERVICE_DESTINATION_PREFIX + (scope != null ? scope + '@' : '') + this.serviceIdentifier + '/' + methodIdentifier
        let eventFactoryToUse = defaultEventFactory
        if (eventFactory) {
            eventFactoryToUse = eventFactory
        }

        let eventBusToUse = this.serviceRegistry.eventBus

        // store additional attribute if there is an active span
        const span = opentelemetry.trace.getActiveSpan()
        if (span) {
            span.setAttribute(ATTR_SERVER_ADDRESS, eventBusToUse.serverInfo?.host || 'unknown')
            span.setAttribute(ATTR_SERVER_PORT, eventBusToUse.serverInfo?.port || 'unknown')
        }

        let event: IEvent = eventFactoryToUse.create(cri, args)

        return eventBusToUse.requestStream(event, sendControlEvents)
                            .pipe(map<IEvent, any>((value: IEvent): any => {
                                const contentType: string | undefined = value.getHeader(EventConstants.CONTENT_TYPE_HEADER)
                                if (contentType !== undefined) {
                                    if (contentType === EventConstants.CONTENT_JSON) {
                                        return JSON.parse(value.getDataString())
                                    } else if (contentType === EventConstants.CONTENT_TEXT) {
                                        return value.getDataString()
                                    } else if (contentType === EventConstants.CONTENT_OCTET_STREAM) {
                                        return value.data.orUndefined()
                                    } else {
                                        throw new Error('Content Type ' + contentType + ' is not supported')
                                    }
                                } else {
                                    return null
                                }
                            }))
    }
}
