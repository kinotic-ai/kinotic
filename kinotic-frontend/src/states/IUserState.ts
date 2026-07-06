import { ConnectedInfo, Kinotic } from '@kinotic-ai/core'
import { isApplicationParticipant, isOrganizationParticipant } from '@kinotic-ai/os-api'
import { reactive } from 'vue'
import { createDebug } from '@/util/debug'
import { apiUrl, createConnectionInfo } from '../util/helpers'

const debug = createDebug('user-state')

export interface IUserState {
    connectedInfo: ConnectedInfo | null

    isAuthenticated(): boolean

    /**
     * Returns the organization id of the authenticated participant. This client admits only
     * organization-scoped participants; it throws for application- and system-scoped participants.
     */
    getOrganizationId(): string

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

export class UserState implements IUserState {
    public connectedInfo: ConnectedInfo | null = null

    private inFlight: Promise<unknown> = Promise.resolve()

    public login(): Promise<void> {
        return this.serialize(async () => {
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

    public getOrganizationId(): string {
        const participant = this.connectedInfo?.participant
        // This client admits organization administrators only. An application-scoped participant
        // also carries an organizationId, so it is excluded explicitly rather than by absence.
        if (!participant || !isOrganizationParticipant(participant) || isApplicationParticipant(participant)) {
            throw new Error('No organization id available — this client requires an organization-scoped session')
        }
        return participant.organizationId
    }

    private serialize<T>(operation: () => Promise<T>): Promise<T> {
        const result = this.inFlight.then(operation)
        this.inFlight = result.catch(() => {})   // the next call waits for this one, pass or fail
        return result
    }
}

export const USER_STATE: IUserState = reactive(new UserState())
