import { ConnectedInfo, Kinotic } from '@kinotic-ai/core'
import { reactive } from 'vue'
import { createDebug } from '@/util/debug'
import { apiUrl, createConnectionInfo } from '../util/helpers'

const debug = createDebug('user-state')

export interface IUserState {
    connectedInfo: ConnectedInfo | null

    isAuthenticated(): boolean

    /**
     * Returns the organization id of the currently authenticated participant. Valid for both
     * organization- and application-scoped logins, which each carry an organization id; throws
     * for a system-scoped participant, which has none.
     */
    getOrganizationId(): string

    /**
     * Opens the realtime connection, authenticated by the session cookie a prior REST login
     * established. Resolves once connected; rejects when there is no valid session — so on app
     * start it doubles as the "is the browser still signed in?" check.
     */
    login(): Promise<void>

    /** Destroys the server session and closes the realtime connection. */
    logout(): Promise<void>
}

export class UserState implements IUserState {
    public connectedInfo: ConnectedInfo | null = null

    public async login(): Promise<void> {
        try {
            await Kinotic.disconnect()
        } catch (error) {
            debug('No existing connection to disconnect')
        }

        try {
            this.connectedInfo = await Kinotic.connect(createConnectionInfo())
        } catch (reason: any) {
            this.connectedInfo = null
            throw new Error(reason ? String(reason) : 'Session authentication failed')
        }
    }

    public async logout(): Promise<void> {
        try {
            await fetch(apiUrl('/api/logout'), { method: 'POST', credentials: 'include' })
        } catch (error) {
            debug('Logout request failed: %O', error)
        }
        try {
            await Kinotic.disconnect()
        } catch (error) {
            debug('Error disconnecting from Kinotic: %O', error)
        }
        this.connectedInfo = null
    }

    public isAuthenticated(): boolean {
        return this.connectedInfo !== null
    }

    public getOrganizationId(): string {
        // The server sends a polymorphic participant that carries `organizationId` for both
        // organization- and application-scoped logins (a system participant carries none). The
        // published core `Participant` type predates that shape, so read the field through a
        // narrowed view rather than the auth-scope fields the server no longer sends.
        const organizationId = (this.connectedInfo?.participant as { organizationId?: string | null } | undefined)?.organizationId
        if (!organizationId) {
            throw new Error('No organization id available — the authenticated participant is not scoped to an organization')
        }
        return organizationId
    }
}

export const USER_STATE: IUserState = reactive(new UserState())
