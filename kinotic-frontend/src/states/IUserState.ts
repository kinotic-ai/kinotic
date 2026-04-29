import { ConnectedInfo, ConnectionInfo, Kinotic } from '@kinotic-ai/core'
import { reactive } from 'vue'
import { createDebug } from '@/util/debug'

const debug = createDebug('user-state');
import { createConnectionInfo } from '../util/helpers'

export interface IUserState {
    connectedInfo: ConnectedInfo | null
    authScopeType: string | null
    authScopeId: string | null

    isAccessDenied(): boolean
    isAuthenticated(): boolean

    /**
     * Returns the organization id of the currently authenticated participant. Only valid for
     * ORGANIZATION-scoped logins, where the JWT's scopeId IS the org id; throws for SYSTEM-
     * or APPLICATION-scoped participants (those need a separate resolution path — TODO).
     */
    getOrganizationId(): string

    /**
     * Authenticates with a Kinotic-minted JWT (the short-lived ticket delivered as a URL
     * fragment after a successful /api/login/callback or /api/signup/complete-org). The JWT
     * carries scopeType and scopeId claims which we lift onto the STOMP CONNECT headers
     * (the gateway cross-checks them against the JWT for defense-in-depth).
     */
    loginWithToken(token: string): Promise<void>

    logout(): Promise<void>
}

export class UserState implements IUserState {
    public connectedInfo: ConnectedInfo | null = null
    public authScopeType: string | null = null
    public authScopeId: string | null = null
    private authenticated: boolean = false
    private accessDenied: boolean = false

    public async loginWithToken(token: string): Promise<void> {
        debug('loginWithToken: start')
        try {
            await Kinotic.disconnect()
            debug('loginWithToken: prior connection disconnected')
        } catch (error) {
            debug('No existing connection to disconnect')
        }

        const claims = decodeJwtPayload(token)
        if (!claims || !claims.scopeType || !claims.scopeId) {
            throw new Error('Token missing scope claims')
        }

        const connectionInfo: ConnectionInfo = createConnectionInfo()
        connectionInfo.connectHeaders = {
            Authorization: `Bearer ${token}`,
            authScopeType: claims.scopeType,
            authScopeId: claims.scopeId
        }

        try {
            this.connectedInfo = await Kinotic.connect(connectionInfo)
            this.authScopeType = claims.scopeType
            this.authScopeId = claims.scopeId
            this.authenticated = true
            this.accessDenied = false
            debug('loginWithToken: success authenticated=true connectedInfo=set')
        } catch (reason: any) {
            this.accessDenied = true
            debug('loginWithToken: FAILED %O', reason)
            throw new Error(reason ? String(reason) : 'Token authentication failed')
        }
    }

    public async logout(): Promise<void> {
        debug('logout: called - resetting auth state')
        console.trace('[user-state] logout called')
        if (this.connectedInfo) {
            try {
                await Kinotic.disconnect()
            } catch (error) {
                debug('Error disconnecting from Kinotic: %O', error)
            }
        }
        this.connectedInfo = null
        this.authScopeType = null
        this.authScopeId = null
        this.authenticated = false
        this.accessDenied = false
    }

    public isAccessDenied(): boolean {
        return this.accessDenied
    }

    public isAuthenticated(): boolean {
        return this.authenticated && this.connectedInfo !== null
    }

    public getOrganizationId(): string {
        if (!this.authScopeId) {
            throw new Error('No organization id available — user is not authenticated')
        }
        if (this.authScopeType !== 'ORGANIZATION') {
            throw new Error(`Cannot resolve organization id: participant is ${this.authScopeType}-scoped, expected ORGANIZATION`)
        }
        return this.authScopeId
    }
}

export const USER_STATE: IUserState = reactive(new UserState())

/**
 * Best-effort decode of a JWT payload without verifying the signature.
 * Used only to read the scopeType/scopeId claims the gateway minted; the gateway re-validates
 * the JWT signature on STOMP CONNECT, so a tampered token will fail there.
 */
function decodeJwtPayload(token: string): Record<string, any> | null {
    try {
        const parts = token.split('.')
        if (parts.length !== 3) return null
        const padded = parts[1] + '==='.slice((parts[1].length + 3) % 4)
        const json = atob(padded.replace(/-/g, '+').replace(/_/g, '/'))
        return JSON.parse(json)
    } catch {
        return null
    }
}
