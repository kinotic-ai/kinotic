import {buildBrokerUrl, buildServerUrl, type ConnectOptions, type IWebSocket, type ServerInfo, SessionKeepAliveMode} from '@/api/ConnectOptions'
import type {CredentialsResolver} from '@/api/security/CredentialsResolver'
import {EventConstants} from '@/api/event/IEventBus'
import {ConnectedInfo} from '@/api/security/ConnectedInfo'
import {type IFrame, RxStomp, RxStompConfig, StompHeaders} from '@stomp/rx-stomp'
import {ReconnectionTimeMode} from '@stomp/stompjs'
import debug from 'debug'
import {Observable, Subject, Subscription} from 'rxjs'
import {v4 as uuidv4} from 'uuid'

/** Fixed REST route the cookie-session pre-flight probes; identical in every environment. */
const SESSION_CHECK_PATH = '/api/auth/me'

/**
 * Creates a new RxStomp client and manages it
 * This is here to simplify the logic needed for connection management and the usage of the client.
 */
export class StompConnectionManager {

    public lastWebsocketError: Event | null = null
    /**
     * This will return true if a {@link ConnectOptions#maxConnectionAttempts} threshold was set and was reached
     */
    public maxConnectionAttemptsReached: boolean = false
    /**
     * Invoked when the server issues a new replyToId on reconnect, which changes {@link replyToCri}.
     * This always happens with {@link SessionKeepAliveMode.NONE} since no session carries the
     * replyToId across connections.
     */
    public replyToCriChangedHandler: ((replyToCri: string) => void) | null = null
    public rxStomp: RxStomp | null = null
    private readonly INITIAL_RECONNECT_DELAY: number = 2000
    private readonly JITTER_MAX: number = 5000
    private readonly MAX_RECONNECT_DELAY: number = 120000 // 2 mins
    private connectionAttempts: number = 0
    private debugLogger = debug('kinoitc:stomp')
    private readonly fatalErrorsSubject: Subject<Error> = new Subject<Error>()
    private readonly _fatalErrors: Observable<Error> = this.fatalErrorsSubject.asObservable()
    private initialConnectionSuccessful: boolean = false
    private serverHeadersSubscription: Subscription | null = null
    private stompErrorsSubscription: Subscription | null = null
    private readonly uuidv4 = uuidv4()

    private _replyToCri: string | null = null

    /**
     * The reply destination CRI for this connection, or null before a connection has been established.
     * It is built from the server-generated replyToId returned in the CONNECTED frame.
     */
    public get replyToCri(): string | null {
        return this._replyToCri
    }

    /**
     * @return true if this {@link StompConnectionManager} is actively trying to maintain a connection to the Stomp server, false if not.
     */
    public get active(): boolean {
        return !!this.rxStomp;
    }

    /**
     * return true if this {@link StompConnectionManager} is active and has a connection to the stomp server
     */
    public get connected(): boolean {
        return this.rxStomp != null
            && this.rxStomp.connected()
    }

    /**
     * Emits when the connection encounters an unrecoverable failure: a STOMP ERROR frame from
     * the server (typically auth/handshake rejection) or a failure of credential resolution or
     * the user-supplied {@link ConnectOptions#webSocketFactory} (e.g. a token refresh failed).
     * Long-running consumers should subscribe to react to terminal failures.
     */
    public get fatalErrors(): Observable<Error> {
        return this._fatalErrors
    }

    public async activate(options: ConnectOptions): Promise<ConnectedInfo> {
        // Validate state and short circuit
        if (!options?.server?.host) {
            throw new Error('No host provided')
        }
        if (this.rxStomp) {
            throw new Error('Stomp connection already active')
        }

        const server: ServerInfo = options.server as ServerInfo
        const credentialsResolver: CredentialsResolver | undefined = options.credentials

        // The connection's auth mode is fixed for its life: a user factory owns the socket
        // outright; otherwise the initial resolution decides between upgrade-header auth and
        // the browser session cookie. Per-attempt resolution below refreshes the headers.
        let headerAuth = false
        if (!options.webSocketFactory) {
            if (!credentialsResolver) {
                throw new Error('No credentials supplied and no default resolution applied')
            }
            const resolved = await credentialsResolver.resolve(server)
            if (resolved === null) {
                throw new Error('No Kinotic credentials found; consulted: ' + credentialsResolver.name)
            }
            headerAuth = resolved.authHeaders != null
        }

        return new Promise((resolve, reject): void => {

            // we reset most state here so, it will persist on a connection failure
            this.connectionAttempts = 0
            this.initialConnectionSuccessful = false
            this.lastWebsocketError = null
            this.maxConnectionAttemptsReached = false

            const url = buildBrokerUrl(server)

            this.rxStomp = new RxStomp()

            // Sockets may be produced asynchronously (a user factory, or credential resolution
            // refreshing a short-lived secret), but @stomp/stompjs only accepts a synchronous
            // factory. So the socket is produced in beforeConnect — which stompjs awaits
            // immediately before creating the socket — and handed back synchronously here.
            let preparedSocket: IWebSocket | null = null
            const userWebSocketFactory = options.webSocketFactory
            const usesPreparedSocket = userWebSocketFactory != null || headerAuth

            const stompConfig: RxStompConfig = {
                brokerURL: url,
                connectHeaders: {
                    [EventConstants.SESSION_KEEP_ALIVE_HEADER]: options.sessionKeepAlive ?? SessionKeepAliveMode.ACTIVITY
                },
                heartbeatIncoming: 120000,
                heartbeatOutgoing: 30000,
                reconnectDelay: this.INITIAL_RECONNECT_DELAY,
                maxReconnectDelay: this.MAX_RECONNECT_DELAY,
                reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
                webSocketFactory: usesPreparedSocket ? () => preparedSocket as IWebSocket : undefined,
                beforeConnect: async (): Promise<void> => {

                    // Cookie-auth clients (browser, headerless credentials) can't read a rejected WS
                    // upgrade, so probe the session over a readable REST status first — same host as
                    // the socket, at the fixed SESSION_CHECK_PATH. A 401 means the cookie isn't (or is
                    // no longer) valid — not retriable, so fail fast: this rejects the initial connect
                    // and surfaces a fatal error on a later reconnect, instead of looping on an
                    // unauthenticated socket. Other statuses (incl. a server without the route) proceed.
                    if(!usesPreparedSocket){
                        const sessionCheckUrl = buildServerUrl(server, 'http') + SESSION_CHECK_PATH
                        try {
                            const res = await fetch(sessionCheckUrl, { credentials: 'include' })
                            if(res.status === 401){
                                // Not signed in (or the session expired) — an expected outcome, not a
                                // failure; fail the connect and the app routes to /login from here.
                                await this.signalFatal(new Error('Authentication required'))
                                return
                            }
                        } catch {
                            // Couldn't reach the check (network/CORS) — treat as transient and let the socket try.
                        }
                    }

                    // If max connections are set, then make sure we have not exceeded that threshold
                    if(options?.maxConnectionAttempts){
                        this.connectionAttempts++

                        if(this.connectionAttempts > options.maxConnectionAttempts){
                            this.maxConnectionAttemptsReached = true
                            // signalFatal rejects activate() via initialFailureSubscription on the initial-connect path.
                            await this.signalFatal(new Error(
                                'Max number of reconnection attempts reached',
                                { cause: this.lastWebsocketError ?? undefined }
                            ))
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
                            await this.signalFatal(new Error('WebSocket factory failed', { cause: e }))
                        }
                    } else if (headerAuth) {
                        // resolved on every attempt so short-lived credentials refresh each connect
                        try {
                            const resolved = await credentialsResolver!.resolve(server)
                            if (resolved?.authHeaders == null) {
                                await this.signalFatal(new Error(
                                    'Credentials resolver no longer supplies auth headers: ' + credentialsResolver!.name))
                            } else {
                                // The Node/Bun WebSocket accepts a `headers` option that the DOM lib typings omit.
                                const WS = WebSocket as unknown as
                                    new (url: string, opts: {headers: Record<string, string>}) => IWebSocket
                                preparedSocket = new WS(url, {headers: resolved.authHeaders})
                            }
                        } catch (e) {
                            await this.signalFatal(new Error('Credential resolution failed', { cause: e }))
                        }
                    }
                }
            }

            if(this.debugLogger.enabled){
                stompConfig.debug = (msg: string): void => {
                    this.debugLogger(msg)
                }
            }

            this.rxStomp.configure(stompConfig)

            // Handles Websocket Errors
            this.rxStomp.webSocketErrors$.subscribe(async value => {
                this.lastWebsocketError = value
                // The attempt that just failed was the last the budget allows — report now
                // instead of paying the reconnect delay before the next beforeConnect notices.
                if (options?.maxConnectionAttempts && this.connectionAttempts >= options.maxConnectionAttempts) {
                    this.maxConnectionAttemptsReached = true
                    await this.signalFatal(new Error(
                        'Max number of reconnection attempts reached',
                        { cause: value ?? undefined }
                    ))
                }
            })

            // Forward STOMP ERROR frames as fatal errors. Server-issued ERROR frames close the
            // connection and indicate an unrecoverable condition (auth failure, protocol error).
            this.stompErrorsSubscription = this.rxStomp.stompErrors$.subscribe(async (frame: IFrame) => {
                const stompError = new Error(frame.headers['message'] as string, { cause: frame })
                await this.signalFatal(new Error('STOMP connection error', { cause: stompError }))
            })

            // Handles Successful Connections
            const connectedSubscription: Subscription = this.rxStomp.connected$.subscribe(() =>{
                // We only want these for the initial connection
                connectedSubscription.unsubscribe()
                initialFailureSubscription.unsubscribe()

                // Successful Connection
                if(!this.initialConnectionSuccessful){
                    this.initialConnectionSuccessful = true
                }
            })

            // Route any fatal error that arrives before the initial connection succeeds into
            // the activate() promise so the caller learns why the connection never came up.
            const initialFailureSubscription: Subscription = this.fatalErrorsSubject.subscribe((err: Error) => {
                connectedSubscription.unsubscribe()
                initialFailureSubscription.unsubscribe()
                reject(err.message)
            })

            // Triggered on every CONNECTED frame, including reconnects. The replyToId is generated
            // server side, so on reconnect it may change.
            this.serverHeadersSubscription = this.rxStomp.serverHeaders$.subscribe(async (value: StompHeaders) => {
                const connectedInfoJson: string | undefined = value[EventConstants.CONNECTED_INFO_HEADER]
                if (connectedInfoJson == null) {
                    if (!this.initialConnectionSuccessful) {
                        await this.deactivate()
                        reject('Server did not return proper data for successful login')
                    }
                    return
                }

                const connectedInfo: ConnectedInfo = JSON.parse(connectedInfoJson)
                if (connectedInfo.replyToId == null) {
                    if (!this.initialConnectionSuccessful) {
                        await this.deactivate()
                        reject('Server did not return a replyToId for successful login')
                    }
                    return
                }

                const newReplyToCri: string = EventConstants.REPLY_DESTINATION_PREFIX
                    + connectedInfo.replyToId + ':' + this.uuidv4
                    + '@kinoitc.js.EventBus/replyHandler'

                if (!this.initialConnectionSuccessful) {
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
        const rxStomp = this.rxStomp
        if(rxStomp){
            await rxStomp.deactivate({force: force})
            // A concurrent activate() (a reconnect, or signalFatal racing an external disconnect) may
            // have replaced the socket while we awaited this teardown, so only clear the shared state
            // if rxStomp is still the one we tore down.
            if(this.rxStomp === rxStomp){
                this.serverHeadersSubscription?.unsubscribe()
                this.serverHeadersSubscription = null
                this.stompErrorsSubscription?.unsubscribe()
                this.stompErrorsSubscription = null
                this.rxStomp = null
                this._replyToCri = null
            }
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

    /**
     * Tears down the connection then publishes the failure to {@link fatalErrors}. Deactivating
     * first means subscribers see the error already in its terminal state — no further reconnection
     * attempts, no live rxStomp — so they can react without racing the cleanup. The error is also
     * available to subscribers via {@link fatalErrors}; this only traces it for local debugging.
     */
    private async signalFatal(err: Error): Promise<void> {
        this.debugLogger('Fatal error, deactivating connection: %O', err)
        await this.deactivate()
        this.fatalErrorsSubject.next(err)
    }

}
