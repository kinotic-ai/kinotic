import { createCRI } from '@/api/event/CRI'
import { EventConstants, type IEvent, type IEventBus } from '@/api/event/IEventBus'
import { ServiceIdentifier } from '@/api/ServiceIdentifier'
import {type ArgumentResolver, JsonArgumentResolver } from './ArgumentResolver'
import { Util } from './Util'
import { BasicReturnValueConverter, type ReturnValueConverter } from './ReturnValueConverter'
import { Subscription } from "rxjs"
import { createDebugLogger, type Logger } from "./Logger"
import type {ContextInterceptor, ServiceContext} from '@/api/ContextInterceptor'
import { receivesContext, scopeOptionalMethodNames } from '@/api/KinoticDecorators'
import opentelemetry, { context, propagation, trace, SpanKind, SpanStatusCode, type Span, type Tracer } from '@opentelemetry/api'
import info from '../../../package.json' with { type: 'json' }

/**
 * Handles invoking services registered with Kinotic in TypeScript.
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
    // Subscription for the address the service is addressable by
    private methodSubscription: Subscription | null = null
    // Present only for a scoped service with ScopeOptional methods: the shared unscoped
    // address every instance listens on for the methods any instance can answer
    private unscopedSubscription: Subscription | null = null
    private readonly methodMap: Record<string, (...args: any[]) => any>
    private readonly scopeOptionalMethods: Set<string>
    private readonly tracer: Tracer

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

        this.log = options.logger || createDebugLogger("kinotic:ServiceInvocationSupervisor")
        this.argumentResolver = options.argumentResolver || new JsonArgumentResolver()
        this.returnValueConverter = options.returnValueConverter || new BasicReturnValueConverter()

        this.methodMap = this.buildMethodMap(serviceInstance)
        this.scopeOptionalMethods = scopeOptionalMethodNames(serviceInstance)
        // Names the instrumentation scope, which is the library emitting the span rather than the
        // application being traced. The application identifies itself through the SDK's resource.
        this.tracer = opentelemetry.trace.getTracer(info.name, info.version)
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

        // Subscribe at the service's zone address, dispatching to the invocation machinery
        const criBase = this.serviceIdentifier.cri().baseResource()
        this.methodSubscription = this.subscribeAt(criBase)
        this.log.info(`ServiceInvocationSupervisor started for ${criBase}`)

        // a scoped service with ScopeOptional methods also joins the shared unscoped
        // address, where any instance may answer the methods that opted in
        if (this.serviceIdentifier.scope && this.scopeOptionalMethods.size > 0) {
            const unscopedBase = this.serviceIdentifier.unscopedCri().baseResource()
            this.unscopedSubscription = this.subscribeAt(unscopedBase)
            this.log.info(`ServiceInvocationSupervisor also listening for ScopeOptional methods at ${unscopedBase}`)
        }
    }

    private subscribeAt(criBase: string): Subscription {
        return this._eventBus
                   .observe(criBase)
                   .subscribe({
                                  next: async (event: IEvent) => {
                                      await this.processEvent(event);
                                  },
                                  error: (error: Error) => {
                                      this.log.error("Event listener error", error)
                                      this.active = false
                                  },
                                  // losing either of the supervisor's addresses makes it inactive
                                  complete: () => {
                                      this.log.error("Event listener stopped unexpectedly. Setting supervisor inactive.")
                                      this.active = false
                                  },
                              })
    }

    public stop(): void {
        if (!this.active) {
            throw new Error("Service already stopped")
        }
        this.active = false

        this.methodSubscription?.unsubscribe()
        this.methodSubscription = null
        this.unscopedSubscription?.unsubscribe()
        this.unscopedSubscription = null

        this.log.info("ServiceInvocationSupervisor stopped")
    }

    private buildMethodMap(serviceInstance: any): Record<string, (...args: any[]) => any> {
        const methodMap: Record<string, (...args: any[]) => any> = {}
        // Walks the prototype chain because Publish registers instances of a subclass whose own
        // prototype is empty; descriptors are used so getters are not invoked while scanning.
        let proto = Object.getPrototypeOf(serviceInstance)
        while (proto && proto !== Object.prototype) {
            for (const key of Object.getOwnPropertyNames(proto)) {
                const descriptor = Object.getOwnPropertyDescriptor(proto, key)
                if (typeof descriptor?.value === "function" && key !== "constructor" && !methodMap[key]) {
                    methodMap[key] = descriptor.value.bind(serviceInstance)
                }
            }
            proto = Object.getPrototypeOf(proto)
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
        const incomingCri = createCRI(event.cri)
        const path = incomingCri.path()
        if (!path) {
            throw new Error("The methodId must not be blank")
        }

        const handlerMethod = this.methodMap[path]
        if (!handlerMethod) {
            throw new Error(`No method resolved for methodId ${path}`)
        }

        // A scoped service answers on the shared unscoped address only for methods that
        // declared themselves instance-independent: an instance-affine method invoked without
        // a scope would execute on whichever instance received it, so it fails loud instead
        if (this.serviceIdentifier.scope && !incomingCri.hasScope() && !this.scopeOptionalMethods.has(path)) {
            throw new Error(`Method ${path} of ${this.serviceIdentifier.qualifiedName()} requires a scoped invocation naming the service instance`)
        }

        const methodName = path;
        const span = this.startInvocationSpan(event, methodName)

        try {
            const args = this.argumentResolver.resolveArguments(event)
            const injectContext = receivesContext(this.serviceInstance, methodName);

            // Create context using interceptor
            let serviceContext: ServiceContext = {};
            const interceptor = this.interceptorProvider();
            if (interceptor) {
                try {
                    serviceContext = await interceptor.intercept(event, serviceContext);
                } catch (e) {
                    this.log.error(`Interceptor failed to create context for event: ${JSON.stringify(event)}`, e)
                    this.handleException(event, new Error("Internal server error"))
                    this.failSpan(span, e)
                    return
                }
            }

            // A @Context method takes the context as its final parameter, appended after caller args
            if (injectContext) {
                args.push(serviceContext);
            }

            const expectedArgsCount = handlerMethod.length
            if (args.length !== expectedArgsCount) {
                throw new Error(`Argument count mismatch for method ${path}: expected ${expectedArgsCount}, got ${args.length}`)
            }

            let result: any
            try {
                // Runs the method under the span so anything it calls, including invocations of other
                // services, is recorded as part of this one
                result = context.with(trace.setSpan(context.active(), span), () => handlerMethod(...args))
                if (result instanceof Promise) {
                    // The method has not finished yet, so the span ends with the promise
                    result.then(
                        (resolved) => {
                            this.processMethodInvocationResult(event, resolved)
                            span.end()
                        },
                        (error) => {
                            this.handleException(event, error)
                            this.failSpan(span, error)
                        }
                    )
                } else {
                    this.processMethodInvocationResult(event, result)
                    span.end()
                }
            } catch (e) {
                this.handleException(event, e)
                this.failSpan(span, e)
            }
        } catch (e) {
            this.failSpan(span, e)
            throw e
        }
    }

    /**
     * Starts the span covering one service method invocation, continuing the caller's trace when the
     * incoming event carries one.
     */
    private startInvocationSpan(event: IEvent, methodName: string): Span {
        const carrier: Record<string, string> = {}
        for (const [key, value] of event.headers.entries()) {
            carrier[key] = value
        }

        const serviceName = this.serviceIdentifier.qualifiedName()
        return this.tracer.startSpan(`${this.serviceIdentifier.name}/${methodName}`,
                                     {
                                         kind: SpanKind.SERVER,
                                         attributes: {
                                             'rpc.system': 'kinotic',
                                             'rpc.service': serviceName,
                                             'rpc.method': methodName
                                         }
                                     },
                                     propagation.extract(context.active(), carrier))
    }

    private failSpan(span: Span, error: any): void {
        span.recordException(error)
        span.setStatus({ code: SpanStatusCode.ERROR })
        span.end()
    }

    private processMethodInvocationResult(event: IEvent, result: any): void {
        const outgoingEvent = this.returnValueConverter.convert(event.headers, result)
        this.sendReply(outgoingEvent)
    }

    /**
     * Sends a reply to the caller that made the request.
     */
    private sendReply(event: IEvent): void {
        try {
            this._eventBus.send(event)
        } catch (e) {
            // The invocation is over and this runs off a promise callback, so there is no caller
            // left to fail and a throw here would surface as an unhandled rejection.
            this.log.warn(`Could not send reply to ${event.cri}`, e)
        }
    }

    private handleException(event: IEvent, error: any): void {
        const errorEvent = Util.createReplyEvent(
            event.headers,
            new Map([
                        [EventConstants.ERROR_HEADER, error.message || "Unknown error"],
                        [EventConstants.CONTENT_TYPE_HEADER, "application/json"]
                    ]),
            new TextEncoder().encode(JSON.stringify({ message: error.message }))
        )
        this.sendReply(errorEvent)
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
        if (!replyTo.startsWith(`${EventConstants.REPLY_DESTINATION_SCHEME}:`)) {
            this.log.warn("Reply-to header must be a valid reply destination")
            return false
        }
        return true
    }
}
