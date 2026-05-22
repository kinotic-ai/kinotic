import { ConnectedInfo, Kinotic } from '@kinotic-ai/core'
import { reactive } from 'vue'
import { createDebug } from '@/util/debug'
import { createConnectionInfo } from '../util/helpers'

const debug = createDebug('user-state')

export interface IUserState {
    connectedInfo: ConnectedInfo | null

    isAuthenticated(): boolean

    /**
     * Returns the organization id of the currently authenticated participant. Only valid for
     * ORGANIZATION-scoped logins, where the participant's authScopeId IS the org id; throws for
     * SYSTEM- or APPLICATION-scoped participants (those need a separate resolution path — TODO).
     */
    getOrganizationId(): string

    /**
     * Opens the realtime connection, authenticated by the session cookie a prior REST login
     * established. Resolves once connected; rejects when there is no valid session — so on app
     * start it doubles as the "is the browser still signed in?" check.
     */
    login(): Promise<void>

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
        if (this.connectedInfo) {
            try {
                await Kinotic.disconnect()
            } catch (error) {
                debug('Error disconnecting from Kinotic: %O', error)
            }
        }
        this.connectedInfo = null
    }

    public isAuthenticated(): boolean {
        return this.connectedInfo !== null
    }

    public getOrganizationId(): string {
        const participant = this.connectedInfo?.participant
        if (!participant?.authScopeId) {
            throw new Error('No organization id available — user is not authenticated')
        }
        if (participant.authScopeType !== 'ORGANIZATION') {
            throw new Error(`Cannot resolve organization id: participant is ${participant.authScopeType}-scoped, expected ORGANIZATION`)
        }
        return participant.authScopeId
    }
}

export const USER_STATE: IUserState = reactive(new UserState())
