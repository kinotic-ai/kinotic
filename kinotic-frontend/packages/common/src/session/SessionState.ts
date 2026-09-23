import { ConnectedInfo, Kinotic } from '@kinotic-ai/core'
import { createDebug } from '../util/debug'
import { apiUrl, serverOverrides } from '../util/helpers'
import { CONNECTION_STATE } from './connectionState'

const debug = createDebug('session-state')

/** How long to wait before asking again when the server cannot answer the session check. */
const PROBE_INITIAL_DELAY_MS = 1000
const PROBE_MAX_DELAY_MS = 15000

export interface ISessionState {
    connectedInfo: ConnectedInfo | null

    isAuthenticated(): boolean

    /**
     * Opens the realtime connection, authenticated by the session cookie a prior REST login
     * established. Resolves once connected; rejects when the gateway says there is no valid session — so
     * on app start it doubles as the "is the browser still signed in?" check. A gateway that cannot
     * answer at all is waited out, so the call stays pending for as long as the server is unreachable.
     * Overlapping login/logout calls run one at a time, so only a single realtime connection is ever open.
     */
    login(): Promise<void>

    /** Destroys the server session and closes the realtime connection. */
    logout(): Promise<void>
}

export class SessionState implements ISessionState {
    public connectedInfo: ConnectedInfo | null = null

    private inFlight: Promise<unknown> = Promise.resolve()

    public login(): Promise<void> {
        return this.serialize(async () => {
            // Cleared before the disconnect below so the connection handler reads the connection
            // ending as a teardown this client asked for rather than one that died on it.
            this.connectedInfo = null

            try {
                await Kinotic.disconnect()
            } catch (error) {
                debug('No existing connection to disconnect')
            }

            // Reject as soon as there is no valid session, rather than letting Kinotic.connect retry
            // the websocket indefinitely — callers (the auth guard on app start) need a settled answer
            // to route to /login.
            if (!await this.probeSession()) {
                throw new Error('Session authentication failed')
            }

            try {
                this.connectedInfo = await Kinotic.connect({server: serverOverrides()})
            } catch (reason: any) {
                this.connectedInfo = null
                throw new Error(reason ? String(reason) : 'Session authentication failed')
            }
        })
    }

    public logout(): Promise<void> {
        return this.serialize(async () => {
            // Cleared first, for the same reason as in login(): the disconnect below is this
            // client's own, not a connection that ended out from under it.
            this.connectedInfo = null

            try {
                await fetch(apiUrl('/api/auth/logout'), { method: 'POST', credentials: 'include' })
            } catch (error) {
                debug('Logout request failed: %O', error)
            }
            try {
                await Kinotic.disconnect()
            } catch (error) {
                debug('Error disconnecting from Kinotic: %O', error)
            }
        })
    }

    public isAuthenticated(): boolean {
        return this.connectedInfo !== null
    }

    /**
     * Asks the gateway whether the browser's session cookie still authenticates this client, resolving
     * true once it does and false once the gateway says it does not.
     */
    private async probeSession(): Promise<boolean> {
        let ret: boolean | null = null
        let delayMs = PROBE_INITIAL_DELAY_MS
        // A server that cannot answer is waited out rather than reported as a session that ended: the
        // cookie outlives an outage, so routing to /login would only offer a page that cannot work either.
        while (ret === null) {
            try {
                const response = await fetch(apiUrl('/api/auth/me'), { credentials: 'include' })
                if (response.ok) {
                    ret = true
                } else if (response.status === 401) {
                    ret = false
                } else {
                    debug('Session check answered %d', response.status)
                }
            } catch (error) {
                debug('Session check could not reach the server: %O', error)
            }
            if (ret === null) {
                CONNECTION_STATE.reachable = false
                await new Promise(resolve => setTimeout(resolve, delayMs))
                delayMs = Math.min(delayMs * 2, PROBE_MAX_DELAY_MS)
            }
        }
        // the gateway answered, so whichever way this went the app is not waiting on the server any more
        CONNECTION_STATE.reachable = true
        return ret
    }

    protected serialize<T>(operation: () => Promise<T>): Promise<T> {
        const result = this.inFlight.then(operation)
        this.inFlight = result.catch(() => {})   // the next call waits for this one, pass or fail
        return result
    }
}
