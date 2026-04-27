import { ConnectedInfo, ConnectionInfo, Kinotic } from '@kinotic-ai/core'
import { reactive } from 'vue'
import { createDebug } from '@/util/debug'

const debug = createDebug('user-state');
import { createConnectionInfo } from '../util/helpers'

export interface IUserState {
    connectedInfo: ConnectedInfo | null

    isAccessDenied(): boolean
    isAuthenticated(): boolean

    /**
     * Authenticates a local (email/password) user via STOMP CONNECT. The credentials are
     * sent on the CONNECT frame; no token is minted, no token is stored — the gateway's
     * SessionManager owns the session for the WebSocket lifetime.
     */
    authenticate(login: string, passcode: string): Promise<void>

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
    private authenticated: boolean = false
    private accessDenied: boolean = false

    public async authenticate(login: string, passcode: string): Promise<void> {
        try {
            await Kinotic.disconnect()
        } catch (error) {
            debug('No existing connection to disconnect')
        }

        const connectionInfo: ConnectionInfo = createConnectionInfo()
        connectionInfo.connectHeaders = {
            login,
            passcode,
            authScopeType: 'ORGANIZATION', //FIXME: remove hard coded org
            authScopeId: 'kinotic-test'
        }

        try {
            this.connectedInfo = await Kinotic.connect(connectionInfo)
            this.authenticated = true
            this.accessDenied = false
        } catch (reason: any) {
            this.accessDenied = true
            throw new Error(reason ? String(reason) : 'Credentials invalid')
        }
    }

    public async loginWithToken(token: string): Promise<void> {
        try {
            await Kinotic.disconnect()
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
            this.authenticated = true
            this.accessDenied = false
        } catch (reason: any) {
            this.accessDenied = true
            throw new Error(reason ? String(reason) : 'Token authentication failed')
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
        this.authenticated = false
        this.accessDenied = false
    }

    public isAccessDenied(): boolean {
        return this.accessDenied
    }

    public isAuthenticated(): boolean {
        return this.authenticated && this.connectedInfo !== null
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
