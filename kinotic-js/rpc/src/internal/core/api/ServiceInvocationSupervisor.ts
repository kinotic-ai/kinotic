/*
 * Copyright 2008-2021 Kinotic and the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License")
 * See https://www.apache.org/licenses/LICENSE-2.0
 */

import { createCRI } from '@/core/api/CRI.js'
import { EventConstants, IEvent, IEventBus } from '@/core/api/IEventBus.js'
import { ServiceIdentifier } from '@/core/api/ServiceIdentifier.js'
import { ArgumentResolver, JsonArgumentResolver } from './ArgumentResolver.js'
import { EventUtil } from './EventUtil.js'
import { BasicReturnValueConverter, ReturnValueConverter } from './ReturnValueConverter.js'
import { Subscription } from "rxjs"
import { createDebugLogger, Logger } from "./Logger.js"
import { ContextInterceptor, ServiceContext } from '@/core/api/ContextInterceptor.js'
import { CONTEXT_METADATA_KEY } from '@/api/ContinuumDecorators.js'

/**
 * Handles invoking services registered with Continuum in TypeScript.
 *
 * @author Navid Mitchell 🤝Grok
 * @since 3/25/2025
 */
export class ServiceInvocationSupervisor {
    private readonly log: Logger
    private active: boolean = false
    private _eventBus: IEventBus
    private readonly interceptorProvider: () => ContextInterceptor<any> | null
    private readonly argumentResolver: ArgumentResolver
    private readonly returnValueConverter: ReturnValueConverter
    private readonly serviceIdentifier: ServiceIdentifier
    private readonly serviceInstance: any
    private methodSubscription: Subscription | null = null
    private readonly methodMap: Record<string, (...args: any[]) => any>

    constructor(
        serviceIdentifier: ServiceIdentifier,
        serviceInstance: any,
        eventBusService: IEventBus,
        interceptorProvider: () => ContextInterceptor<any> | null,
        options: {
            logger?: Logger
            argumentResolver?: ArgumentResolver
            returnValueConverter?: ReturnValueConverter
        } = {}
    ) {
        if (!serviceIdentifier) throw new Error("ServiceIdentifier must not be null")
        if (!serviceInstance) throw new Error("Service instance must not be null")
        if (!eventBusService) throw new Error("IEventBus must not be null")
        if (!interceptorProvider) throw new Error("interceptorProvider must not be null")

        this.serviceIdentifier = serviceIdentifier
        this.serviceInstance = serviceInstance
        this._eventBus = eventBusService
        this.interceptorProvider = interceptorProvider

        this.log = options.logger || createDebugLogger("continuum:ServiceInvocationSupervisor")
        this.argumentResolver = options.argumentResolver || new JsonArgumentResolver()
        this.returnValueConverter = options.returnValueConverter || new BasicReturnValueConverter()

        this.methodMap = this.buildMethodMap(serviceInstance)
    }

    public isActive(): boolean {
        return this.active
    }

    /**
     * The {@link IEventBus} that this supervisor uses to listen for service invocation events
     * This can be changed at runtime, the supervisor will restart to use the new event bus
     * @param eventBus the new IEventBus to use
     */
    public set eventBus(eventBus: IEventBus) {
        if (this.active) {
            this.stop();
            this._eventBus = eventBus;
            this.start();
        }else{
            this._eventBus = eventBus;
        }
    }

    public start(): void {
        if (this.active) {
            throw new Error("Service already started")
        }
        this.active = true

        const criBase = this.serviceIdentifier.cri().baseResource()
        this.methodSubscription = this._eventBus
                                      .observe(criBase)
                                      .subscribe({
                                                     next: async (event: IEvent) => {
                                                         await this.processEvent(event);
                                                     },
                                                     error: (error: Error) => {
                                                         this.log.error("Event listener error", error)
                                                         this.active = false
                                                     },
                                                     complete: () => {
                                                         this.log.error("Event listener stopped unexpectedly. Setting supervisor inactive.")
                                                         this.active = false
                                                     },
                                                 })

        this.log.info(`ServiceInvocationSupervisor started for ${criBase}`)
    }

    public stop(): void {
        if (!this.active) {
            throw new Error("Service already stopped")
        }
        this.active = false

        if (this.methodSubscription) {
            this.methodSubscription.unsubscribe()
            this.methodSubscription = null
        }

        this.log.info("ServiceInvocationSupervisor stopped")
    }

    private buildMethodMap(serviceInstance: any): Record<string, (...args: any[]) => any> {
        const methodMap: Record<string, (...args: any[]) => any> = {}
        for (const key of Object.getOwnPropertyNames(Object.getPrototypeOf(serviceInstance))) {
            const method = serviceInstance[key]
            if (typeof method === "function" && key !== "constructor") {
                methodMap[key] = method.bind(serviceInstance)
            }
        }
        return methodMap
    }

    private async processEvent(event: IEvent): Promise<void> {
        const isControl = event.hasHeader(EventConstants.CONTROL_HEADER)
        this.log.trace(`Service ${isControl ? "Control" : "Invocation"} requested for ${event.cri}`)

        try {
            if (isControl) {
                this.processControlPlaneRequest(event)
            } else {
                if (this.validateReplyTo(event)) {
                    await this.processInvocationRequest(event)
                } else {
                    this.log.error(`ReplyTo header missing or invalid. Ignoring event: ${JSON.stringify(event)}`)
                }
            }
        } catch (e) {
            this.log.debug(`Exception processing service request: ${JSON.stringify(event)}`, e)
            this.handleException(event, e)
        }
    }

    private processControlPlaneRequest(event: IEvent): void {
        const correlationId = event.getHeader(EventConstants.CORRELATION_ID_HEADER)
        if (!correlationId) {
            throw new Error("Streaming control plane messages require a CORRELATION_ID_HEADER")
        }
        this.log.trace(`Processing control event for correlationId: ${correlationId}`)
    }

    private async processInvocationRequest(event: IEvent): Promise<void> {
        const path = createCRI(event.cri).path()
        if (!path) {
            throw new Error("The methodId must not be blank")
        }

        const handlerMethod = this.methodMap[path]
        if (!handlerMethod) {
            throw new Error(`No method resolved for methodId ${path}`)
        }

        const methodName = path;
        const args = this.argumentResolver.resolveArguments(event)
        const contextIndices: number[] = Reflect.getMetadata(CONTEXT_METADATA_KEY, this.serviceInstance, methodName) || [];

        // Create context using interceptor
        let context: ServiceContext = {};
        const interceptor = this.interceptorProvider();
        if (interceptor) {
            try {
                context = await interceptor.intercept(event, context);
            } catch (e) {
                this.log.error(`Interceptor failed to create context for event: ${JSON.stringify(event)}`, e)
                this.handleException(event, new Error("Internal server error"))
                return
            }
        }

        // Inject context into arguments where @Context is used
        for (const index of contextIndices) {
            args[index] = context;
        }

        const expectedArgsCount = handlerMethod.length
        if (args.length !== expectedArgsCount) {
            throw new Error(`Argument count mismatch for method ${path}: expected ${expectedArgsCount}, got ${args.length}`)
        }

        let result: any
        try {
            result = handlerMethod(...args)
            if (result instanceof Promise) {
                result.then(
                    (resolved) => this.processMethodInvocationResult(event, resolved),
                    (error) => this.handleException(event, error)
                )
            } else {
                this.processMethodInvocationResult(event, result)
            }
        } catch (e) {
            this.handleException(event, e)
        }
    }

    private processMethodInvocationResult(event: IEvent, result: any): void {
        const outgoingEvent = this.returnValueConverter.convert(event.headers, result)
        this._eventBus.send(outgoingEvent)
    }

    private handleException(event: IEvent, error: any): void {
        const errorEvent = EventUtil.createReplyEvent(
            event.headers,
            new Map([
                        [EventConstants.ERROR_HEADER, error.message || "Unknown error"],
                        [EventConstants.CONTENT_TYPE_HEADER, "application/json"]
                    ]),
            new TextEncoder().encode(JSON.stringify({ message: error.message }))
        )
        this._eventBus.send(errorEvent)
    }

    private validateReplyTo(event: IEvent): boolean {
        const replyTo = event.getHeader(EventConstants.REPLY_TO_HEADER)
        if (!replyTo) {
            this.log.warn("No reply-to header found in event")
            return false
        }
        if (replyTo.trim() === "") {
            this.log.warn("Reply-to header must not be blank")
            return false
        }
        if (!replyTo.startsWith(`${EventConstants.SERVICE_DESTINATION_SCHEME}:`)) {
            this.log.warn("Reply-to header must be a valid service destination")
            return false
        }
        return true
    }
}
