/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {ConnectionInfo, ServerInfo} from '@/api/ConnectionInfo'
import {ContinuumError} from '@/api/errors/ContinuumError'
import {ConnectedInfo} from '@/api/security/ConnectedInfo'
import {StompConnectionManager} from '@/core/api/StompConnectionManager'
import {context, propagation} from '@opentelemetry/api';
import {IFrame, IMessage} from '@stomp/rx-stomp'
import {ConnectableObservable, firstValueFrom, Observable, Subject, Subscription, throwError, Unsubscribable} from 'rxjs'
import {filter, map, multicast} from 'rxjs/operators'
import {Optional} from 'typescript-optional'
import {v4 as uuidv4} from 'uuid'
import {EventConstants, IEvent, IEventBus} from './IEventBus'

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
        this.data.ifPresent(( value ) => ret = new TextDecoder().decode(value))
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

    public fatalErrors: Observable<Error>
    public serverInfo: ServerInfo | null = null
    private stompConnectionManager: StompConnectionManager = new StompConnectionManager()
    private replyToCri: string  | null = null
    private requestRepliesObservable: ConnectableObservable<IEvent> | null = null
    private requestRepliesSubject: Subject<IEvent> | null = null
    private requestRepliesSubscription: Subscription | null = null
    private errorSubject: Subject<IFrame> = new Subject<IFrame>()
    private errorSubjectSubscription: Subscription | null | undefined = null

    constructor() {
        this.fatalErrors = this.errorSubject
                               .pipe(map<IFrame, Error>((frame: IFrame): Error => {
                                   this.disconnect()
                                       .catch((error: string) => {
                                           if(console){
                                               console.error('Error disconnecting from Stomp: ' + error)
                                           }
                                       })
                                   // TODO: map to continuum error
                                   return new ContinuumError(frame.headers['message'])
                               }))
        this.stompConnectionManager.deactivationHandler = () => {
            this.cleanup()
        }
    }

    public isConnectionActive(): boolean{
        return this.stompConnectionManager.active
    }

    public isConnected(): boolean {
        return this.stompConnectionManager.connected
    }

    public async connect(connectionInfo: ConnectionInfo): Promise<ConnectedInfo> {
        if(!this.stompConnectionManager.active){

            // reset state in case connection ended due to max connection attempts
            this.cleanup()

            const connectedInfo = await this.stompConnectionManager.activate(connectionInfo)
            // manually copy so we don't store any sensitive info
            this.serverInfo = new ServerInfo()
            this.serverInfo.host = connectionInfo.host
            this.serverInfo.port = connectionInfo.port
            this.serverInfo.useSSL = connectionInfo.useSSL

            // FIXME: a reply should not need a reply, therefore a replyCri probably should not be a EventConstants.SERVICE_DESTINATION_PREFIX
            this.replyToCri = this.stompConnectionManager.replyToCri

            this.errorSubjectSubscription = this.stompConnectionManager.rxStomp?.stompErrors$.subscribe(this.errorSubject)

            return connectedInfo
        }else{
            throw new Error('Event Bus connection already active')
        }
    }

    public async disconnect(force?: boolean): Promise<void> {
        await this.stompConnectionManager.deactivate(force)

        this.cleanup()
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
                    this.requestRepliesObservable = this._observe(this.replyToCri as string)
                                                        .pipe(multicast(this.requestRepliesSubject)) as ConnectableObservable<IEvent>
                    this.requestRepliesSubscription = this.requestRepliesObservable.connect()
                }

                let serverSignaledCompletion = false
                const correlationId = uuidv4()
                const defaultMessagesSubscription: Unsubscribable
                          = this.requestRepliesObservable
                                .pipe(filter((value: IEvent): boolean => {
                                    return value.headers.get(EventConstants.CORRELATION_ID_HEADER) === correlationId
                                })).subscribe({
                                                  next(value: IEvent): void {

                                                      if (value.hasHeader(EventConstants.CONTROL_HEADER)) {

                                                          if (value.headers.get(EventConstants.CONTROL_HEADER) === 'complete') {
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
        if (this.requestRepliesSubject != null) {

            // This will be sent to any client waiting on an Event
            this.requestRepliesSubject.error(new Error('Connection disconnected'))

            if (this.requestRepliesSubscription != null) {
                this.requestRepliesSubscription.unsubscribe()
                this.requestRepliesSubscription = null
            }

            this.requestRepliesSubject = null;
            this.requestRepliesObservable = null
        }

        if (this.errorSubjectSubscription) {
            this.errorSubjectSubscription.unsubscribe()
            this.errorSubjectSubscription = null
        }

        this.serverInfo = null
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
     * This is internal impl of observe that creates a cold observable.
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
                                   destination = message.headers[prop]
                               }else{
                                   headers.set(prop, message.headers[prop])
                               }
                           }

                           return new Event(destination, headers, message.binaryBody)
                       }))
        }else{
            throw this.createSendUnavailableError()
        }
    }

}

