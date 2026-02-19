/*
 * Copyright 2008-2021 Kinotic and the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License")
 * See https://www.apache.org/licenses/LICENSE-2.0
 */

import { ContinuumContextStack } from '@/api/Continuum'
import debug from 'debug'
import { ServiceIdentifier } from './ServiceIdentifier.js'
import { ServiceInvocationSupervisor } from '@/internal/core/api/ServiceInvocationSupervisor.js'
import opentelemetry, { SpanKind, SpanStatusCode, Tracer } from '@opentelemetry/api'
import {
    ATTR_SERVER_ADDRESS,
    ATTR_SERVER_PORT,
} from '@opentelemetry/semantic-conventions'
import { Observable } from 'rxjs'
import { first, map } from 'rxjs/operators'
import info from '../../../package.json' assert { type: 'json' }
import { Event } from './EventBus'
import { EventConstants, IEvent, IEventBus } from './IEventBus'
import { IEventFactory, IServiceProxy, IServiceRegistry } from './IServiceRegistry'
import { ContextInterceptor, ServiceContext } from './ContextInterceptor'

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
export class ServiceRegistry implements IServiceRegistry {
    private _eventBus: IEventBus
    private supervisors: Map<string, ServiceInvocationSupervisor> = new Map()
    private contextInterceptor: ContextInterceptor<any> | null = null
    private debugLogger = debug('continuum:serviceRegistry')

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
            const supervisor = new ServiceInvocationSupervisor(
                serviceIdentifier,
                service,
                this.eventBus,
                () => this.contextInterceptor
            )
            this.supervisors.set(criString, supervisor)
            supervisor.start()
        }
    }

    public unRegister(serviceIdentifier: ServiceIdentifier): void {
        const criString = serviceIdentifier.cri().raw()
        const supervisor = this.supervisors.get(criString)
        if (supervisor) {
            this.debugLogger(`Unregistering service for CRI: ${criString}`)
            supervisor.stop()
            this.supervisors.delete(criString)
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
        this.tracer = opentelemetry.trace.getTracer(
            'continuum.client',
            info.version
        )
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
                    span.setAttribute('continuum.scope', scope)
                }
                span.setAttribute('rpc.system', 'continuum')
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
        } else if (ContinuumContextStack.getEventFactory()) {
            eventFactoryToUse = ContinuumContextStack.getEventFactory()!
        }

        let eventBusToUse = this.serviceRegistry.eventBus
        if (ContinuumContextStack.getContinuumInstance()) {
            eventBusToUse = ContinuumContextStack.getContinuumInstance()!.eventBus
        }

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
                                    if (contentType === 'application/json') {
                                        return JSON.parse(value.getDataString())
                                    } else if (contentType === 'text/plain') {
                                        return value.getDataString()
                                    } else {
                                        throw new Error('Content Type ' + contentType + ' is not supported')
                                    }
                                } else {
                                    return null
                                }
                            }))
    }
}
