import {type ConnectOptions, ServerInfo} from '@/api/ConnectOptions'
import {ConnectedInfo} from '@/api/security/ConnectedInfo'
import {StompConnectionManager} from '@/internal/api/StompConnectionManager'
import {context, propagation} from '@opentelemetry/api';
import type {IMessage} from '@stomp/rx-stomp';
import {ConnectableObservable, firstValueFrom, Observable, Subject, Subscription, throwError, type Unsubscribable} from 'rxjs'
import {filter, map, multicast, tap} from 'rxjs/operators'
import {Optional} from 'typescript-optional'
import {v4 as uuidv4} from 'uuid'
import {EventConstants, type IEvent, type IEventBus} from './IEventBus'

/**
 * Default IEvent implementation
 */
export class Event implements IEvent {

    public cri: string
    public headers: Map<string, string>
    public data: Optional<Uint8Array>

    constructor(cri: string,
                headers?: Map<string, string>,
                data?: Uint8Array) {

        this.cri = cri

        if (headers !== undefined) {
            this.headers = headers
        } else {
            this.headers = new Map<string, string>()
        }

        this.data = Optional.ofNullable(data)
    }

    public getHeader(key: string): string | undefined {
        return this.headers.get(key)
    }

    public hasHeader(key: string): boolean {
        return this.headers.has(key)
    }

    public setHeader(key: string, value: string): void {
        this.headers.set(key, value)
    }

    public removeHeader(key: string): boolean {
        return this.headers.delete(key)
    }

    public setDataString(data: string): void {
        const uint8Array = new TextEncoder().encode(data)
        this.data = Optional.ofNonNull(uint8Array)
    }

    public getDataString(): string {
        let ret = ''
        this.data.ifPresent(( value: any ) => ret = new TextDecoder().decode(value))
        return ret
    }
}

interface Carrier {
    traceparent?: string;
    tracestate?: string;
}

/**
 * Default implementation of {@link IEventBus}
 */
export class EventBus implements IEventBus {

    public serverInfo: ServerInfo | null = null
    private stompConnectionManager: StompConnectionManager = new StompConnectionManager()
    private connectionLifecycle: Promise<unknown> = Promise.resolve()
    private replyToCri: string  | null = null
    private requestRepliesObservable: ConnectableObservable<IEvent> | null = null
    private requestRepliesSubject: Subject<IEvent> | null = null
    private requestRepliesSubscription: Subscription | null = null
    private readonly activeCorrelationIds: Set<string> = new Set<string>()
    private readonly recentlyReaped: Set<string> = new Set<string>()
    // How long a sent cancel suppresses repeat cancels for the same stream before retrying.
    private static readonly REAP_DEBOUNCE_MS = 5000

    constructor() {
        // We send an error any in-flight requests and clean up our connection state on fatal errors
        // The StompConnectionManager will automatically deactivate on fatal errors
        this.stompConnectionManager.fatalErrors.subscribe(() => this.cleanup())
        this.stompConnectionManager.replyToCriChangedHandler = (replyToCri: string) => {
            this.replyToCri = replyToCri
            this.resetRequestReplies('Reply destination changed')
        }
    }

    public get fatalErrors(): Observable<Error> {
        return this.stompConnectionManager.fatalErrors
    }

    public isConnectionActive(): boolean{
        return this.stompConnectionManager.active
    }

    public isConnected(): boolean {
        return this.stompConnectionManager.connected
    }

    public connect(options: ConnectOptions): Promise<ConnectedInfo> {
        return this.serializeLifecycle(async () => {
            if(!this.stompConnectionManager.active){

                // reset state in case connection ended due to max connection attempts
                this.cleanup()

                const connectedInfo = await this.stompConnectionManager.activate(options)
                // manually copy so we don't store any sensitive info
                this.serverInfo = new ServerInfo()
                this.serverInfo.host = options.host as string
                this.serverInfo.port = options.port
                this.serverInfo.useSSL = options.useSSL

                this.replyToCri = this.stompConnectionManager.replyToCri

                return connectedInfo
            }else{
                throw new Error('Event Bus connection already active')
            }
        })
    }

    public disconnect(force?: boolean): Promise<void> {
        return this.serializeLifecycle(async () => {
            await this.stompConnectionManager.deactivate(force)
            this.cleanup()
        })
    }

    /** Runs connect and disconnect one at a time so they never overlap on the shared socket. */
    private serializeLifecycle<T>(operation: () => Promise<T>): Promise<T> {
        // A one-at-a-time promise queue. `connectionLifecycle` always holds a promise that resolves
        // when the previously queued operation finished, so running `operation` off it defers
        // `operation` until that one is done. We then store this operation's own completion back
        // into `connectionLifecycle`, so the next call defers behind this one — and so on.
        const result = this.connectionLifecycle.then(operation)
        // `result` is the operation's real outcome and goes to the caller. The copy we keep as the
        // next "previous" swallows rejections, so one failed operation can't reject the chain and
        // stall everything queued behind it.
        this.connectionLifecycle = result.catch(() => {})
        return result
    }

    public send(event: IEvent): void {
        if(this.stompConnectionManager.rxStomp){
            const headers: any = {}

            for (const [key, value] of event.headers.entries()) {
                headers[key] = value
            }

            const carrier: Carrier = {}
            propagation.inject(context.active(), carrier)
            if(carrier.traceparent){
                headers[EventConstants.TRACEPARENT_HEADER] = carrier.traceparent
            }
            if(carrier.tracestate){
                headers[EventConstants.TRACESTATE_HEADER] = carrier.tracestate
            }

            // send data over stomp
            this.stompConnectionManager.rxStomp.publish({
                                                            destination: event.cri,
                                                            headers,
                                                            binaryBody: event.data.orUndefined()
                                                        })
        }else{
            throw this.createSendUnavailableError()
        }
    }

    public request(event: IEvent): Promise<IEvent> {
        return firstValueFrom(this.requestStream(event, false))
    }

    public requestStream(event: IEvent, sendControlEvents: boolean = true): Observable<IEvent> {
        if(this.stompConnectionManager?.rxStomp){
            return new Observable<IEvent>((subscriber) => {

                if (this.requestRepliesObservable == null) {
                    this.requestRepliesSubject = new Subject<IEvent>()
                    // Reaper: before multicast, so it runs once per reply to cancel streams we no longer track.
                    this.requestRepliesObservable = this._observe(this.replyToCri as string)
                                                        .pipe(tap((value: IEvent) => this.cancelIfUnexpected(value)),
                                                              multicast(this.requestRepliesSubject)) as ConnectableObservable<IEvent>
                    this.requestRepliesSubscription = this.requestRepliesObservable.connect()
                }

                let serverSignaledCompletion = false
                const correlationId = uuidv4()
                this.activeCorrelationIds.add(correlationId)
                const defaultMessagesSubscription: Unsubscribable
                          = this.requestRepliesObservable
                                .pipe(filter((value: IEvent): boolean => {
                                    return value.headers.get(EventConstants.CORRELATION_ID_HEADER) === correlationId
                                })).subscribe({
                                                  next(value: IEvent): void {

                                                      if (value.hasHeader(EventConstants.CONTROL_HEADER)) {

                                                          if (value.headers.get(EventConstants.CONTROL_HEADER) === EventConstants.CONTROL_VALUE_COMPLETE) {
                                                              serverSignaledCompletion = true
                                                              subscriber.complete()
                                                          } else {
                                                              throw new Error('Control Header ' + value.headers.get(EventConstants.CONTROL_HEADER) + ' is not supported')
                                                          }

                                                      } else if (value.hasHeader(EventConstants.ERROR_HEADER)) {

                                                          // TODO: add custom error type that contains error detail as well if provided by server, this would be the event body
                                                          serverSignaledCompletion = true
                                                          subscriber.error(new Error(value.getHeader(EventConstants.ERROR_HEADER)))

                                                      } else {

                                                          subscriber.next(value)

                                                      }
                                                  },
                                                  error(err: any): void {
                                                      subscriber.error(err)
                                                  },
                                                  complete(): void {
                                                      subscriber.complete()
                                                  }
                                              })

                subscriber.add(defaultMessagesSubscription)

                event.setHeader(EventConstants.REPLY_TO_HEADER, this.replyToCri as string)
                event.setHeader(EventConstants.CORRELATION_ID_HEADER, correlationId)

                this.send(event)

                return () => {
                    this.activeCorrelationIds.delete(correlationId)
                    if (sendControlEvents && !serverSignaledCompletion) {
                        // create control event to cancel long-running request
                        const controlEvent: Event = new Event(event.cri)
                        controlEvent.setHeader(EventConstants.CONTROL_HEADER, EventConstants.CONTROL_VALUE_CANCEL)
                        controlEvent.setHeader(EventConstants.CORRELATION_ID_HEADER, correlationId)
                        this.send(controlEvent)
                    }
                }
            })
        }else{
            return throwError(() => this.createSendUnavailableError())
        }
    }

    public listen(_serverInfo: ServerInfo): Promise<void> {
        return Promise.reject('Not implemented')
    }

    public observe(cri: string): Observable<IEvent> {
        return this._observe(cri)
    }

    private cleanup(): void{
        this.resetRequestReplies('Connection disconnected')

        this.serverInfo = null
    }

    /**
     * Cancels a server stream whose reply arrived for a correlationId we no longer track. The server can't
     * detect this itself since all requests share one reply destination, so the cancel is sent to the
     * originating service, whose CRI the server includes on each reply.
     */
    private cancelIfUnexpected(value: IEvent): void {
        const correlationId = value.getHeader(EventConstants.CORRELATION_ID_HEADER)
        if (correlationId === undefined || this.activeCorrelationIds.has(correlationId)) {
            return
        }
        const originCri = value.getHeader(EventConstants.ORIGIN_CRI_HEADER)
        // The server may have many values already in flight when we cancel, and each one re-enters here.
        // recentlyReaped suppresses those duplicates so we send one cancel per stale stream, not one per
        // stray value.
        if (originCri === undefined || this.recentlyReaped.has(correlationId)) {
            return
        }
        try {
            const cancelEvent: Event = new Event(originCri)
            cancelEvent.setHeader(EventConstants.CONTROL_HEADER, EventConstants.CONTROL_VALUE_CANCEL)
            cancelEvent.setHeader(EventConstants.CORRELATION_ID_HEADER, correlationId)
            this.send(cancelEvent)

            // Expire the entry after the debounce window so a lost cancel self-heals: if the stream is
            // still sending by then, the next stray value falls through the guard above and we cancel again.
            this.recentlyReaped.add(correlationId)
            setTimeout(() => this.recentlyReaped.delete(correlationId), EventBus.REAP_DEBOUNCE_MS)
        } catch {
            // best-effort: if the send fails we retry on the next stray value
        }
    }

    /**
     * Tears down the shared request-replies stream so the next request rebuilds it against the
     * current {@link replyToCri}. Any in-flight requests are failed with the given reason since
     * their replies can no longer be delivered.
     */
    private resetRequestReplies(reason: string): void {
        if (this.requestRepliesSubject != null) {

            this.requestRepliesSubject.error(new Error(reason))

            if (this.requestRepliesSubscription != null) {
                this.requestRepliesSubscription.unsubscribe()
                this.requestRepliesSubscription = null
            }

            this.requestRepliesSubject = null
            this.requestRepliesObservable = null
            this.recentlyReaped.clear()
        }
    }

    /**
     * Creates the proper error to return if this.stompConnectionManager?.rxStomp is not available on a send request
     */
    private createSendUnavailableError(): Error {
        let ret: string = 'You must call connect on the event bus before sending any request'
        if(this.stompConnectionManager.maxConnectionAttemptsReached){
            ret = 'Max connection attempts reached event bus is not available'
        }
        return new Error(ret)
    }

    /**
     * This is an internal impl of observe that creates a cold observable.
     * The public variants transform this to some type of hot observable depending on the need
     * @param cri to observe
     * @return the cold {@link Observable<IEvent>} for the given destination
     */
    private _observe(cri: string): Observable<IEvent> {
        if(this.stompConnectionManager?.rxStomp) {
            return this.stompConnectionManager
                       .rxStomp
                       .watch(cri)
                       .pipe(map<IMessage, IEvent>((message: IMessage): IEvent => {

                           // We translate all IMessage objects to IEvent objects
                           const headers: Map<string, string> = new Map<string, string>()
                           let destination: string = ''
                           for (const prop of Object.keys(message.headers)) {
                               if (prop === 'destination') {
                                   destination = message.headers[prop] as string
                               }else{
                                   headers.set(prop, message.headers[prop] as string)
                               }
                           }

                           return new Event(destination, headers, message.binaryBody)
                       }))
        }else{
            throw this.createSendUnavailableError()
        }
    }

}

