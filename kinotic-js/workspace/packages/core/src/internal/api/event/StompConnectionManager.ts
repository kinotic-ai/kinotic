import {ConnectionInfo, SessionKeepAliveMode} from '@/api/ConnectionInfo'
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
    public deactivationHandler: (() => void) | null = null

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

            const url = 'ws' + (connectionInfo.useSSL ? 's' : '')
                + '://' + connectionInfo.host
                + (connectionInfo.port ? ':' + connectionInfo.port : '') + '/v1'

            this.rxStomp = new RxStomp()

            const stompConfig: RxStompConfig = {
                brokerURL: url,
                connectHeaders: {
                    [EventConstants.SESSION_KEEP_ALIVE_HEADER]: connectionInfo.sessionKeepAlive
                },
                heartbeatIncoming: 120000,
                heartbeatOutgoing: 30000,
                reconnectDelay: this.INITIAL_RECONNECT_DELAY,
                webSocketFactory: connectionInfo.webSocketFactory,
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
                    serverHeadersSubscription.unsubscribe()

                    if (connectedInfo.replyToId != null) {
                        // The replyToId is generated server side; the client builds its reply
                        // destination from it once the CONNECTED frame arrives.
                        this._replyToCri = EventConstants.REPLY_DESTINATION_PREFIX
                            + connectedInfo.replyToId + ':' + this.uuidv4
                            + '@kinoitc.js.EventBus/replyHandler'
                        resolve(connectedInfo)
                    } else {
                        reject('Server did not return a replyToId for successful login')
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
