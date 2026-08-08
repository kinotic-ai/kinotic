import { ConnectedInfo, Kinotic } from '@kinotic-ai/core'
import { createDebug } from '../util/debug'
import { apiUrl, serverOverrides } from '../util/helpers'

const debug = createDebug('session-state')

export interface ISessionState {
    connectedInfo: ConnectedInfo | null

    isAuthenticated(): boolean

    /**
     * Opens the realtime connection, authenticated by the session cookie a prior REST login
     * established. Resolves once connected; rejects when there is no valid session — so on app
     * start it doubles as the "is the browser still signed in?" check. Overlapping login/logout
     * calls run one at a time, so only a single realtime connection is ever open.
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
            try {
                await Kinotic.disconnect()
            } catch (error) {
                debug('No existing connection to disconnect')
            }

            this.connectedInfo = null

            // Reject immediately when the backend is unreachable or there is no valid session,
            // rather than letting Kinotic.connect retry the websocket indefinitely — callers
            // (the auth guard on app start) need a settled answer to route to /login.
            const sessionCheck = await fetch(apiUrl('/api/auth/me'), { credentials: 'include' })
            if (!sessionCheck.ok) {
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
            this.connectedInfo = null
        })
    }

    public isAuthenticated(): boolean {
        return this.connectedInfo !== null
    }

    protected serialize<T>(operation: () => Promise<T>): Promise<T> {
        const result = this.inFlight.then(operation)
        this.inFlight = result.catch(() => {})   // the next call waits for this one, pass or fail
        return result
    }
}
