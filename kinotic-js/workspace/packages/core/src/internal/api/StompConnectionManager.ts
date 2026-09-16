import {buildBrokerUrl, buildServerUrl, type ConnectOptions, type IWebSocket, type ServerInfo, SessionKeepAliveMode} from '@/api/ConnectOptions'
import type {CredentialsResolver} from '@/api/security/CredentialsResolver'
import {EventConstants} from '@/api/event/IEventBus'
import {ConnectedInfo} from '@/api/security/ConnectedInfo'
import {type IFrame, RxStomp, RxStompConfig, StompHeaders, RxStompState} from '@stomp/rx-stomp'
import {ReconnectionTimeMode} from '@stomp/stompjs'
import debug from 'debug'
import {skip} from 'rxjs/operators'
import {v4 as uuidv4} from 'uuid'
import {StompActivation} from './StompActivation'

/** Fixed REST route the cookie-session pre-flight probes; identical in every environment. */
const SESSION_CHECK_PATH = '/api/auth/me'

/**
 * Owns the process's single RxStomp client and manages its connection lifecycle.
 * The client is created once and reused across activate/deactivate cycles, so
 * subscriptions made through it (service registrations, observed CRIs) survive a
 * disconnect and are re-subscribed by RxStomp on the next activation. Each activate()
 * owns its own listeners and pending state, so a deactivate() only ever ends the
 * activation it was called for, and an activate() waits for an in-flight deactivate().
 */
export class StompConnectionManager {

    public lastWebsocketError: Event | null = null
    /**
     * This will return true if a {@link ConnectOptions#maxConnectionAttempts} threshold was set and was reached
     */
    public maxConnectionAttemptsReached: boolean = false

    /**
     * Invoked once each time a connection ends: a drop before any reconnect, a failure that ends it for
     * good, or deactivate(). The reason is the failure that ended it, or null when nothing failed. The
     * server releases everything it held for the connection at the same moment, so nothing in flight can
     * complete any more. Whether the connection is being retried afterwards is {@link active}.
     */
    public connectionEndedHandler: ((reason: Error | null) => void) | null = null
    /**
     * The process-lifetime RxStomp client. Never replaced: watch() subscriptions made on it
     * queue until connected and re-subscribe on every (re)connection, which is what keeps
     * published services and observed CRIs alive across disconnect/connect cycles.
     */
    public readonly rxStomp: RxStomp = new RxStomp()
    // the connection being maintained; null while inactive
    private activation: StompActivation | null = null
    // a deactivate() still closing its socket; the next activate() waits for it
    private teardown: Promise<void> | null = null
    private readonly INITIAL_RECONNECT_DELAY: number = 2000
    private readonly JITTER_MAX: number = 5000
    private readonly MAX_RECONNECT_DELAY: number = 120000 // 2 mins
    private connectionAttempts: number = 0
    private debugLogger = debug('kinotic:stomp')
    private initialConnectionSuccessful: boolean = false
    private rxStompHasConnected: boolean = false

    private _replyToCri: string | null = null

    /**
     * The reply destination CRI of the current connection, or null while there is none. It is minted on
     * every CONNECTED frame from the server-generated replyToId and a discriminator of this connection's
     * own, so the consumer the server still holds for a previous socket never receives this one's replies.
     */
    public get replyToCri(): string | null {
        return this._replyToCri
    }

    /**
     * @return true if this {@link StompConnectionManager} is actively trying to maintain a connection to the Stomp server, false if not.
     */
    public get active(): boolean {
        return this.activation !== null
    }

    /**
     * return true if this {@link StompConnectionManager} is active and has a connection to the stomp server
     */
    public get connected(): boolean {
        return this.activation !== null && this.rxStomp.connected()
    }

    public async activate(options: ConnectOptions): Promise<ConnectedInfo> {
        // Validate state and short circuit
        if (!options?.server?.host) {
            throw new Error('No host provided')
        }
        if (this.activation) {
            throw new Error('Stomp connection already active')
        }
        // a deactivate() still closing its socket finishes before a new socket is opened
        await this.teardown
        if (this.activation) {
            throw new Error('Stomp connection already active')
        }

        const server: ServerInfo = options.server as ServerInfo
        const credentialsResolver: CredentialsResolver | undefined = options.credentials

        // Claimed before credential resolution, so a deactivate() from here on ends this activation
        const activation = new StompActivation()
        this.activation = activation

        // The connection's auth mode is fixed for its life: a user factory owns the socket
        // outright; otherwise the initial resolution decides between upgrade-header auth and
        // the browser session cookie. Per-attempt resolution below refreshes the headers.
        let headerAuth = false
        try {
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
            if (activation.ended) {
                throw new Error('Deactivated before the connection was established')
            }
        } catch (e) {
            if (this.activation === activation) {
                this.activation = null
            }
            throw e
        }

        return new Promise((resolve, reject): void => {

            // we reset most state here so, it will persist on a connection failure
            this.connectionAttempts = 0
            this.initialConnectionSuccessful = false
            this.lastWebsocketError = null
            this.maxConnectionAttemptsReached = false

            const url = buildBrokerUrl(server)

            // Sockets may be produced asynchronously (a user factory, or credential resolution
            // refreshing a short-lived secret), but @stomp/stompjs only accepts a synchronous
            // factory. So the socket is produced in beforeConnect — which stompjs awaits
            // immediately before creating the socket — and handed back synchronously here. The
            // activation holds it until stompjs takes it, so one it never takes is closed with the
            // activation instead of being orphaned.
            const usesPreparedSocket = options.webSocketFactory != null || headerAuth
            const takePreparedSocket = (): IWebSocket => {
                const socket = activation.preparedSocket as IWebSocket
                activation.preparedSocket = null
                return socket
            }

            const stompConfig: RxStompConfig = {
                brokerURL: url,
                connectHeaders: {
                    [EventConstants.SESSION_KEEP_ALIVE_HEADER]: options.sessionKeepAlive ?? SessionKeepAliveMode.ACTIVITY
                },
                // Both match the gateway's 30 s heartbeat, so a gateway that vanishes without closing the
                // socket is closed here once it has been silent for two intervals, the same bound the gateway
                // applies to a client
                heartbeatIncoming: 30000,
                heartbeatOutgoing: 30000,
                reconnectDelay: this.INITIAL_RECONNECT_DELAY,
                maxReconnectDelay: this.MAX_RECONNECT_DELAY,
                reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
                webSocketFactory: usesPreparedSocket ? takePreparedSocket : undefined,
                beforeConnect: (): Promise<void> => {
                    const attempt = this.prepareAttempt(activation, options, server, headerAuth)
                    // stompjs resumes its _connect in the microtasks after the attempt settles and opens a socket
                    // for whichever activation is active then; the teardown of this one waits one macrotask past
                    // that so the next activation cannot be active yet
                    const settled = (): Promise<void> => new Promise(resolve => setTimeout(resolve, 0))
                    activation.attempt = attempt.then(settled, settled)
                    return attempt
                }
            }

            if(this.debugLogger.enabled){
                stompConfig.debug = (msg: string): void => {
                    this.debugLogger(msg)
                }
            }

            this.rxStomp.configure(stompConfig)

            // Handles Websocket Errors
            activation.own(this.rxStomp.webSocketErrors$.subscribe(async value => {
                this.lastWebsocketError = value
                // The attempt that just failed was the last the budget allows — report now
                // instead of paying the reconnect delay before the next beforeConnect notices.
                if (!activation.ended && options?.maxConnectionAttempts && this.connectionAttempts >= options.maxConnectionAttempts) {
                    this.maxConnectionAttemptsReached = true
                    await this.failConnection(activation, new Error(
                        'Max number of reconnection attempts reached',
                        { cause: value ?? undefined }
                    ))
                }
            }))

            // Server-issued ERROR frames close the connection and indicate an unrecoverable
            // condition (auth failure, protocol error), so one ends the connection here.
            activation.own(this.rxStomp.stompErrors$.subscribe(async (frame: IFrame) => {
                const stompError = new Error(frame.headers['message'] as string, { cause: frame })
                await this.failConnection(activation, new Error('STOMP connection error', { cause: stompError }))
            }))

            // The first connection of this activation: from here the connect can only resolve. Each connection
            // gets the full attempt budget again for its reconnects.
            activation.own(this.rxStomp.connected$.subscribe(() =>{
                activation.established()
                this.initialConnectionSuccessful = true
                this.connectionAttempts = 0
            }))

            // A failure or a deactivate() before the socket opened rejects the connect with its reason
            activation.pending(reject)

            // Triggered on every CONNECTED frame, including reconnects; each one mints this connection's
            // reply destination. serverHeaders$ is a BehaviorSubject on an rxStomp that outlives a
            // deactivate/activate cycle, so a later activation is replayed the previous connection's frame
            // on subscribe; skipping it stops activate() resolving with a destination that is gone. Stays on
            // serverHeaders$ rather than connected$: rx-stomp emits the headers before it reports OPEN, so
            // the destination is set before anything can be sent on the new connection.
            activation.own(this.rxStomp.serverHeaders$
                                                 .pipe(skip(this.rxStompHasConnected ? 1 : 0))
                                                 .subscribe(async (value: StompHeaders) => {
                this.rxStompHasConnected = true

                const connectedInfoJson: string | undefined = value[EventConstants.CONNECTED_INFO_HEADER]
                if (connectedInfoJson == null) {
                    if (!this.initialConnectionSuccessful) {
                        await this.deactivate(undefined, new Error('Server did not return proper data for successful login'))
                    }
                    return
                }

                const connectedInfo: ConnectedInfo = JSON.parse(connectedInfoJson)
                if (connectedInfo.replyToId == null) {
                    if (!this.initialConnectionSuccessful) {
                        await this.deactivate(undefined, new Error('Server did not return a replyToId for successful login'))
                    }
                    return
                }

                // A fresh discriminator per connection: the server keeps the previous socket's reply
                // consumer until its heartbeat times out, and two consumers on one address would share
                // the replies
                const newReplyToCri: string = EventConstants.REPLY_DESTINATION_PREFIX
                    + connectedInfo.replyToId + ':' + uuidv4()
                    + '@kinotic.js.EventBus/replyHandler'

                this._replyToCri = newReplyToCri
                if (!this.initialConnectionSuccessful) {
                    resolve(connectedInfo)
                }
            }))

            // An open socket dropping: the socket and everything the server held behind it are gone. A failed
            // reconnect attempt also ends in CLOSED, but from CONNECTING, and loses nothing new. A close that
            // deactivate() itself causes is reported by deactivate().
            let previousState: RxStompState = this.rxStomp.connectionState$.getValue()
            activation.own(this.rxStomp.connectionState$.subscribe((state: RxStompState) => {
                if (state === RxStompState.CLOSED && previousState === RxStompState.OPEN && this.activation === activation) {
                    // the client reconnects from here, so the end carries no reason
                    this.connectionEndedHandler?.(null)
                }
                previousState = state
            }))

            this.rxStomp.activate()
        })
    }

    /**
     * One connection attempt of the activation, run by stompjs before it opens the socket: the session
     * check, the attempt budget, the reconnect jitter, and the socket or headers the attempt needs.
     * A failing outcome ends the activation from here and the attempt still resolves.
     */
    private async prepareAttempt(activation: StompActivation,
                                 options: ConnectOptions,
                                 server: ServerInfo,
                                 headerAuth: boolean): Promise<void> {
        const credentialsResolver: CredentialsResolver | undefined = options.credentials
        const userWebSocketFactory = options.webSocketFactory
        const usesPreparedSocket = userWebSocketFactory != null || headerAuth
        const url = buildBrokerUrl(server)

        // Cookie-auth clients (browser, headerless credentials) can't read a rejected WS
        // upgrade, so probe the session over a readable REST status first — same host as
        // the socket, at the fixed SESSION_CHECK_PATH. A 401 means the cookie isn't (or is
        // no longer) valid — not retriable, so fail fast: this rejects the initial connect
        // and ends the connection on a later reconnect, instead of looping on an
        // unauthenticated socket. Other statuses (incl. a server without the route) proceed.
        if(!usesPreparedSocket){
            const sessionCheckUrl = buildServerUrl(server, 'http') + SESSION_CHECK_PATH
            try {
                const res = await fetch(sessionCheckUrl, { credentials: 'include' })
                if(res.status === 401){
                    // Not signed in (or the session expired) — an expected outcome, not a
                    // failure; fail the connect and the app routes to /login from here.
                    await this.failConnection(activation, new Error('Authentication required'))
                    return
                }
            } catch (e) {
                // Couldn't reach the check (network/CORS) — treat as transient and let the socket
                // try; the connection attempt that follows reports the real outcome.
                this.debugLogger('Session check at %s failed: %O', sessionCheckUrl, e)
            }
        }

        // If max connections are set, then make sure we have not exceeded that threshold
        if(options?.maxConnectionAttempts){
            this.connectionAttempts++

            if(this.connectionAttempts > options.maxConnectionAttempts){
                this.maxConnectionAttemptsReached = true
                // failConnection rejects a still-pending activate() with this reason
                await this.failConnection(activation, new Error(
                    'Max number of reconnection attempts reached',
                    { cause: this.lastWebsocketError ?? undefined }
                ))
                return
            }else{
                await this.connectionJitterDelay(activation);
            }
        }else{
            await this.connectionJitterDelay(activation);
        }
        // a deactivate() during the delay ends the attempt before it produces anything
        if (activation.ended) {
            return
        }

        if(userWebSocketFactory){
            try {
                activation.preparedSocket = await userWebSocketFactory()
            } catch (e) {
                await this.failConnection(activation, new Error('WebSocket factory failed', { cause: e }))
            }
        } else if (headerAuth) {
            // resolved on every attempt so short-lived credentials refresh each connect
            try {
                const resolved = await credentialsResolver!.resolve(server)
                if (resolved?.authHeaders == null) {
                    await this.failConnection(activation, new Error(
                        'Credentials resolver no longer supplies auth headers: ' + credentialsResolver!.name))
                } else {
                    // The Node/Bun WebSocket accepts a `headers` option that the DOM lib typings omit.
                    const WS = WebSocket as unknown as
                        new (url: string, opts: {headers: Record<string, string>}) => IWebSocket
                    activation.preparedSocket = new WS(url, {headers: resolved.authHeaders})
                }
            } catch (e) {
                await this.failConnection(activation, new Error('Credential resolution failed', { cause: e }))
            }
        }
        // a deactivate() landed while the socket was being produced; stompjs will not take it
        if (activation.ended && activation.preparedSocket) {
            activation.preparedSocket.close()
            activation.preparedSocket = null
        }
    }

    /**
     * Ends the current activation and reports the connection's end to {@link connectionEndedHandler}. A
     * second call while the socket is still closing returns the same teardown; a call while inactive
     * resolves at once.
     * @param force close the socket without a DISCONNECT frame
     * @param reason the failure that ended the connection, or null when this is an orderly end
     */
    public deactivate(force?: boolean, reason: Error | null = null): Promise<void> {
        const activation = this.activation
        let ret: Promise<void>
        if (activation) {
            this.activation = null
            activation.end()
            const wasOpen = this.rxStomp.connected()
            this._replyToCri = null
            // a connect still waiting for its socket is settled now, not after the close round trip, and
            // with the failure that ended the connection rather than the generic reason
            activation.failIfPending(reason ?? new Error('Deactivated before the connection was established'))
            // a socket produced for stompjs that it never took
            activation.preparedSocket?.close()
            activation.preparedSocket = null
            // stompjs resolves at once while an attempt is still in beforeConnect, so the attempt is waited for too
            const teardown: Promise<void> = this.rxStomp.deactivate({force: force})
                                                .then(() => activation.attempt ?? undefined)
                                                .finally(() => {
                // watch() subscriptions survive deactivation and re-subscribe on the next activation; the
                // listeners this activation owns end with it
                activation.unsubscribeAll()
                this.teardown = null
            })
            this.teardown = teardown
            // Reported after the teardown is stored, so a connect() issued from the handler waits for it in
            // activate(), and before the close completes, so a socket that never finishes closing cannot
            // withhold the news. A failure ends the connection even when no socket was open, and there the
            // reason is the whole of the news.
            if (wasOpen || reason !== null) {
                this.connectionEndedHandler?.(reason)
            }
            ret = teardown
        } else {
            ret = this.teardown ?? Promise.resolve()
        }
        return ret
    }

    /**
     * Make sure clients don't all try to reconnect at the same time.
     */
    private async connectionJitterDelay(activation: StompActivation): Promise<void> {
        if(this.initialConnectionSuccessful) {
            const randomJitter = Math.random() * this.JITTER_MAX;
            this.debugLogger(`Adding ${randomJitter}ms of jitter delay`)
            await activation.delay(randomJitter)
        }
    }

    /**
     * Ends the activation the failure belongs to, which reports the connection's end with the failure as
     * its reason. The connection is already in its terminal state when the report goes out — no further
     * reconnection attempts — so a subscriber reacts without racing the cleanup.
     */
    private async failConnection(activation: StompActivation, err: Error): Promise<void> {
        // deactivate() has ended and reported this activation; a failure of its attempt is nobody's news
        if (!activation.ended) {
            this.debugLogger('Connection failed, ending it: %O', err)
            await this.deactivate(undefined, err)
        }
    }

}
