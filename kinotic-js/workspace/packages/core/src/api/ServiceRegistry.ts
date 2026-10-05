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
import { serviceContractOf } from './KinoticDecorators'
import type { ServiceContract } from './ServiceContract'

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
 * The default implementation of {@link IServiceRegistry}
 */
/**
 * The platform service a runtime publishes the contracts of its checked services through, in the zone an
 * organization's runtimes reach.
 */
const SERVICE_CONTRACT_SERVICE = 'app-api~org.kinotic.app.api.services.ServiceContractService'
// an application's zone is app.<organizationId>.<applicationId>, with the service's own labels after it
const APPLICATION_ZONE = /^app\.[^.]+\.([^.]+)(\.|$)/

export class ServiceRegistry implements IServiceRegistry {
    private _eventBus: IEventBus
    private supervisors: Map<string, ServiceInvocationSupervisor> = new Map()
    // the contracts of the checked services registered, by CRI, published once the connection is up
    private contracts: Map<string, ServiceContract> = new Map()
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
            // read before the supervisor starts, so a class whose contract cannot be built never serves
            const contract = serviceContractOf(service, serviceIdentifier)
            const supervisor = new ServiceInvocationSupervisor(
                serviceIdentifier,
                service,
                this.eventBus,
                () => this.contextInterceptor
            )
            this.supervisors.set(criString, supervisor)
            supervisor.start()
            if (contract) {
                this.contracts.set(criString, contract)
                if (this.eventBus.isConnected()) {
                    this.publishContract(contract).catch(error => this.debugLogger(`Failed to publish the contract of ${criString}`, error))
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
            this.contracts.delete(criString)
        }
    }

    /**
     * Publishes the contract of every checked service registered so far to the platform's directory, so the
     * platform checks requests to them; a contract the platform refuses fails the publication. The contract of
     * a service registered later is published as it registers.
     */
    public async publishContracts(): Promise<void> {
        for (const contract of this.contracts.values()) {
            await this.publishContract(contract)
        }
    }

    /**
     * The contracts of the checked services registered, by the CRI each is registered under.
     */
    public registeredContracts(): ReadonlyMap<string, ServiceContract> {
        return this.contracts
    }

    // A checked service is published in its application's zone, which names the application the contract
    // registers with; a service in any other zone is the platform's own, whose contract the platform holds already
    private async publishContract(contract: ServiceContract): Promise<void> {
        const application = APPLICATION_ZONE.exec(contract.zone)
        if (application) {
            this.debugLogger(`Publishing the contract of ${contract.zone}~${contract.namespace}.${contract.name}`)
            await this.serviceProxy(SERVICE_CONTRACT_SERVICE).invoke('register', [application[1], contract])
        }
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
