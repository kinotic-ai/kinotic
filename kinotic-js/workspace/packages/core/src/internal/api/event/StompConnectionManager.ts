import {ConnectionInfo, IWebSocket, SessionKeepAliveMode} from '@/api/ConnectionInfo'
import {EventConstants} from '@/api/event/IEventBus'
import {ConnectedInfo} from '@/api/security/ConnectedInfo'
import {type IFrame, RxStomp, RxStompConfig, StompHeaders} from '@stomp/rx-stomp'
import {ReconnectionTimeMode} from '@stomp/stompjs'
import debug from 'debug'
import {Subscription} from 'rxjs'
import {v4 as uuidv4} from 'uuid'

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
    private debugLogger = debug('kinoitc:stomp')
    private readonly uuidv4 = uuidv4()
    private _replyToCri: string | null = null
    private serverHeadersSubscription: Subscription | null = null
    public deactivationHandler: (() => void) | null = null
    /**
     * Invoked when the server issues a new replyToId on reconnect, which changes {@link replyToCri}.
     * This always happens with {@link SessionKeepAliveMode.NONE} since no session carries the
     * replyToId across connections.
     */
    public replyToCriChangedHandler: ((replyToCri: string) => void) | null = null

    /**
     * @return true if this {@link StompConnectionManager} is actively trying to maintain a connection to the Stomp server, false if not.
     */
    public get active(): boolean {
        return !!this.rxStomp;
    }

    /**
     * The reply destination CRI for this connection, or null before a connection has been established.
     * It is built from the server-generated replyToId returned in the CONNECTED frame.
     */
    public get replyToCri(): string | null {
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
            this._replyToCri = null
            this.serverHeadersSubscription?.unsubscribe()
            this.serverHeadersSubscription = null

            const url = 'ws' + (connectionInfo.useSSL ? 's' : '')
                + '://' + connectionInfo.host
                + (connectionInfo.port ? ':' + connectionInfo.port : '') + '/v1'

            this.rxStomp = new RxStomp()

            // The webSocketFactory may be async (e.g. a Node client refreshing a token
            // before each connect), but @stomp/stompjs only accepts a synchronous factory.
            // So the socket is produced in beforeConnect — which stompjs awaits immediately
            // before creating the socket — and handed back synchronously here.
            let preparedSocket: IWebSocket | null = null
            const userWebSocketFactory = connectionInfo.webSocketFactory

            const stompConfig: RxStompConfig = {
                brokerURL: url,
                connectHeaders: {
                    [EventConstants.SESSION_KEEP_ALIVE_HEADER]: connectionInfo.sessionKeepAlive
                },
                heartbeatIncoming: 120000,
                heartbeatOutgoing: 30000,
                reconnectDelay: this.INITIAL_RECONNECT_DELAY,
                webSocketFactory: userWebSocketFactory ? () => preparedSocket as IWebSocket : undefined,
                beforeConnect: async (): Promise<void> => {

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
                            return
                        }else{
                            await this.connectionJitterDelay();
                        }
                    }else{
                        await this.connectionJitterDelay();
                    }

                    if(userWebSocketFactory){
                        try {
                            preparedSocket = await userWebSocketFactory()
                        } catch (e) {
                            // The factory could not produce a socket (e.g. a token refresh
                            // failed). Give up rather than reconnect-loop the same failure.
                            await this.deactivate()
                            if(!this.initialConnectionSuccessful) {
                                reject(e)
                            }
                        }
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

            // Triggered on every CONNECTED frame, including reconnects. The replyToId is generated
            // server side, so on reconnect it may change (it always does with
            // SessionKeepAliveMode.NONE since no session carries it across connections).
            this.serverHeadersSubscription = this.rxStomp.serverHeaders$.subscribe((value: StompHeaders) => {
                const connectedInfoJson: string | undefined = value[EventConstants.CONNECTED_INFO_HEADER]
                const firstConnect: boolean = this._replyToCri == null

                if (connectedInfoJson == null) {
                    if (firstConnect) {
                        reject('Server did not return proper data for successful login')
                    }
                    return
                }

                const connectedInfo: ConnectedInfo = JSON.parse(connectedInfoJson)
                if (connectedInfo.replyToId == null) {
                    if (firstConnect) {
                        reject('Server did not return a replyToId for successful login')
                    }
                    return
                }

                const newReplyToCri: string = EventConstants.REPLY_DESTINATION_PREFIX
                    + connectedInfo.replyToId + ':' + this.uuidv4
                    + '@kinoitc.js.EventBus/replyHandler'

                if (firstConnect) {
                    this._replyToCri = newReplyToCri
                    resolve(connectedInfo)
                } else if (this._replyToCri !== newReplyToCri) {
                    this._replyToCri = newReplyToCri
                    this.replyToCriChangedHandler?.(newReplyToCri)
                }
            })

            this.rxStomp.activate()
        })
    }

    public async deactivate(force?: boolean): Promise<void> {
        if(this.rxStomp){
            await this.rxStomp.deactivate({force: force})
            this.serverHeadersSubscription?.unsubscribe()
            this.serverHeadersSubscription = null
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
