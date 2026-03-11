import {ConnectionInfo} from '@/api/ConnectionInfo'
import {ConnectedInfo} from '@/api/security/ConnectedInfo'
import {EventConstants} from '@/core/api/IEventBus'
import {IFrame, RxStomp, RxStompConfig, StompHeaders} from '@stomp/rx-stomp'
import {ReconnectionTimeMode} from '@stomp/stompjs'
import {Subscription} from 'rxjs'
import {v4 as uuidv4} from 'uuid'
import debug from 'debug'

/**
 * Creates a new RxStomp client and manages it
 * This is here to simplify the logic needed for connection management and the usage of the client.
 */
export class StompConnectionManager {

    public lastWebsocketError: Event | null = null
    /**
     * This will return true if a {@link ConnectionInfo#maxConnectionAttempts} threshold was set and was reached
     */
    public maxConnectionAttemptsReached: boolean = false
    public rxStomp: RxStomp | null = null
    private readonly INITIAL_RECONNECT_DELAY: number = 2000
    private readonly MAX_RECONNECT_DELAY: number = 120000 // 2 mins
    private readonly JITTER_MAX: number = 5000
    private connectionAttempts: number = 0
    private initialConnectionSuccessful: boolean = false
    private debugLogger = debug('continuum:stomp')
    private readonly uuidv4 = uuidv4()
    private replyToId = uuidv4()
    public _replyToCri =  EventConstants.SERVICE_DESTINATION_PREFIX + this.replyToId + ':' + this.uuidv4 + '@continuum.js.EventBus/replyHandler'
    public deactivationHandler: (() => void) | null = null

    /**
     * @return true if this {@link StompConnectionManager} is actively trying to maintain a connection to the Stomp server, false if not.
     */
    public get active(): boolean {
        return !!this.rxStomp;
    }

    public get replyToCri(): string {
        return this._replyToCri
    }

    /**
     * return true if this {@link StompConnectionManager} is active and has a connection to the stomp server
     */
    public get connected(): boolean {
        return this.rxStomp != null
            && this.rxStomp.connected()
    }

    public activate(connectionInfo: ConnectionInfo): Promise<ConnectedInfo> {
        return new Promise((resolve, reject): void => {
            // Validate state and short circuit
            if(!connectionInfo){
                reject('You must supply a valid connectionInfo object')
                return
            }

            if (!(connectionInfo.host)) {
                reject('No host provided')
                return
            }

            if(this.rxStomp) {
                reject('Stomp connection already active')
                return
            }

            // we reset most state here so, it will persist on a connection failure
            this.connectionAttempts = 0
            this.initialConnectionSuccessful = false
            this.lastWebsocketError = null
            this.maxConnectionAttemptsReached = false

            const url = 'ws' + (connectionInfo.useSSL ? 's' : '')
                + '://' + connectionInfo.host
                + (connectionInfo.port ? ':' + connectionInfo.port : '') + '/v1'

            this.rxStomp = new RxStomp()

            let connectHeadersInternal: StompHeaders = (typeof connectionInfo.connectHeaders !== 'function' && connectionInfo.connectHeaders != null ? connectionInfo.connectHeaders : {})

            const stompConfig: RxStompConfig = {
                brokerURL: url,
                connectHeaders: connectHeadersInternal,
                heartbeatIncoming: 120000,
                heartbeatOutgoing: 30000,
                reconnectDelay: this.INITIAL_RECONNECT_DELAY,
                beforeConnect: async (): Promise<void> => {

                    if(typeof connectionInfo.connectHeaders === 'function'){
                        const headers = await connectionInfo.connectHeaders()
                        for(const key in headers) {
                            connectHeadersInternal[key] = headers[key]
                        }
                    }

                    if(connectionInfo.disableStickySession){
                        connectHeadersInternal[EventConstants.DISABLE_STICKY_SESSION_HEADER] = 'true'
                    }

                    // use replyToId if provided in connectionInfo, otherwise set it
                    if(connectHeadersInternal[EventConstants.REPLY_TO_ID_HEADER]){
                        this.replyToId = connectHeadersInternal[EventConstants.REPLY_TO_ID_HEADER]
                        this._replyToCri =  EventConstants.SERVICE_DESTINATION_PREFIX + this.replyToId + ':' + this.uuidv4 + '@continuum.js.EventBus/replyHandler'
                    }else{
                        connectHeadersInternal[EventConstants.REPLY_TO_ID_HEADER] = this.replyToId
                    }

                    // If max connections are set then make sure we have not exceeded that threshold
                    if(connectionInfo?.maxConnectionAttempts){
                        this.connectionAttempts++

                       if(this.connectionAttempts > connectionInfo.maxConnectionAttempts){

                           // Reached threshold give up
                           this.maxConnectionAttemptsReached = true
                           await this.deactivate()

                           // If we have not made an initial connection, the promise is not yet resolved
                           if(!this.initialConnectionSuccessful) {
                               let message = (this.lastWebsocketError as any)?.message ? (this.lastWebsocketError as any)?.message : 'UNKNOWN'
                               reject(`Max number of reconnection attempts reached. Last WS Error ${message}`)
                           }
                       }else{
                           await this.connectionJitterDelay();
                       }
                   }else{
                        await this.connectionJitterDelay();
                   }
               }
            }

            if(this.debugLogger.enabled){
                stompConfig.debug = (msg: string): void => {
                    this.debugLogger(msg)
                }
            }

            //*** Begin Block that handles backoff ***
            this.rxStomp.configure(stompConfig)

            // Set values that are only accessible from the stompClient
            this.rxStomp.stompClient.maxReconnectDelay = this.MAX_RECONNECT_DELAY
            this.rxStomp.stompClient.reconnectTimeMode = ReconnectionTimeMode.EXPONENTIAL

            // Handles Websocket Errors
            this.rxStomp.webSocketErrors$.subscribe(value => {
                this.lastWebsocketError = value
            })

            // Handles Successful Connections
            const connectedSubscription: Subscription = this.rxStomp.connected$.subscribe(() =>{
                connectedSubscription.unsubscribe()
                // Successful Connection
                if(!this.initialConnectionSuccessful){
                    this.initialConnectionSuccessful = true
                }
            })

            // This subscription is to handle any errors that occur during connection
            const errorSubscription: Subscription = this.rxStomp.stompErrors$.subscribe((value: IFrame) => {
                errorSubscription.unsubscribe()
                const message = value.headers['message']
                this.rxStomp?.deactivate()
                this.rxStomp = null
                reject(message)
            })

            // This is triggered when the server sends a CONNECTED frame.
            const serverHeadersSubscription: Subscription = this.rxStomp.serverHeaders$.subscribe((value: StompHeaders) => {
                let connectedInfoJson: string | undefined = value[EventConstants.CONNECTED_INFO_HEADER]
                if (connectedInfoJson != null) {

                    const connectedInfo: ConnectedInfo = JSON.parse(connectedInfoJson)

                    if(!connectionInfo.disableStickySession){

                        serverHeadersSubscription.unsubscribe()

                        if (connectedInfo.sessionId != null && connectedInfo.replyToId != null) {

                            // Remove all information originally sent from the connect headers
                            if (connectionInfo.connectHeaders != null) {
                                for (let key in connectHeadersInternal) {
                                    delete connectHeadersInternal[key]
                                }
                            }

                            connectHeadersInternal[EventConstants.SESSION_HEADER] = connectedInfo.sessionId

                            resolve(connectedInfo)
                        } else {
                            reject('Server did not return proper data for successful login')
                        }

                    }else if(typeof connectionInfo.connectHeaders === 'function'){
                        // If the connect headers are supplied by a function we remove all the header values since they will be recreated on next connect
                        for (let key in connectHeadersInternal) {
                            delete connectHeadersInternal[key]
                        }
                        if(!this.initialConnectionSuccessful) {
                            resolve(connectedInfo)
                        }
                    }else if(typeof connectionInfo.connectHeaders === 'object'){
                        // static object we must leave intact for reuse
                        serverHeadersSubscription.unsubscribe()
                        resolve(connectedInfo)
                    }
                } else {
                    reject('Server did not return proper data for successful login')
                }
            })

            this.rxStomp.activate()
        })
    }

    public async deactivate(force?: boolean): Promise<void> {
        if(this.rxStomp){
            await this.rxStomp.deactivate({force: force})
            if(this.deactivationHandler){
                this.deactivationHandler()
            }
            this.rxStomp = null
        }
        return
    }

    /**
     * Make sure clients don't all try to reconnect at the same time.
     */
    private async connectionJitterDelay(): Promise<void> {
        if(this.initialConnectionSuccessful) {
            const randomJitter = Math.random() * this.JITTER_MAX;
            this.debugLogger(`Adding ${randomJitter}ms of jitter delay`)
            return new Promise(resolve => setTimeout(resolve, randomJitter));
        }
    }

}
