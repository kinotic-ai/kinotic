import { ConnectedInfo, ConnectionInfo, Continuum } from '@mindignited/continuum-client'
import { reactive } from 'vue'
import Cookies from 'js-cookie'
import { User } from 'oidc-client-ts'
import { createDebug } from '@/util/debug'

const debug = createDebug('user-state');
import { oidcSessionManager } from '@/util/OidcSessionManager'
import { createConnectionInfo } from '../util/helpers'

export interface IUserState {
    connectedInfo: ConnectedInfo | null
    oidcUser: User | null

    isAccessDenied(): boolean
    isAuthenticated(): boolean
    authenticate(login: string, passcode: string): Promise<void>
    handleOidcLogin(user: User, provider: string): Promise<void>
    logout(): Promise<void>
}

export class UserState implements IUserState {
    public connectedInfo: ConnectedInfo | null = null
    public oidcUser: User | null = null
    private authenticated: boolean = false
    private accessDenied: boolean = false

    public async authenticate(login: string, passcode: string): Promise<void> {
        try {
            await Continuum.disconnect()
        } catch (error) {
            debug('No existing connection to disconnect')
        }

        const connectionInfo: ConnectionInfo = createConnectionInfo()
        connectionInfo.connectHeaders = {
            login,
            passcode
        }

        try {
            this.connectedInfo = await Continuum.connect(connectionInfo)
            this.authenticated = true
            this.accessDenied = false
            // Note: We intentionally do NOT store basic auth credentials in cookies
            // This is more secure - users must re-login on page refresh
        } catch (reason: any) {
            this.accessDenied = true
            if (reason) {
                throw new Error(reason)
            } else {
                throw new Error('Credentials invalid')
            }
        }
    }

    public async handleOidcLogin(user: User, provider: string): Promise<void> {
        try {
            await Continuum.disconnect()
        } catch (error) {
            debug('No existing connection to disconnect')
        }

        const connectionInfo: ConnectionInfo = createConnectionInfo()

        let tokenToUse = user.access_token;

        if (user.access_token && !this.isValidJWT(user.access_token)) {
            debug('Access token is not a valid JWT, using ID token for Microsoft social login');
            tokenToUse = user.id_token || user.access_token;
        }

        connectionInfo.connectHeaders = {
            Authorization: `Bearer ${tokenToUse}`
        }

        try {
            this.connectedInfo = await Continuum.connect(connectionInfo)
            this.authenticated = true
            this.accessDenied = false
            this.oidcUser = user

            const useSecureCookies = window.location.protocol === 'https:'

            Cookies.set('token', tokenToUse, {
                sameSite: 'strict',
                secure: useSecureCookies,
                expires: new Date(user.expires_at! * 1000)
            })

            if (user.refresh_token) {
                const refreshExpiry = this.parseJwtExpiry(user.refresh_token)
                Cookies.set('oidc_refresh_token', user.refresh_token, {
                    sameSite: 'strict',
                    secure: true,
                    expires: refreshExpiry ?? new Date(Date.now() + 30 * 24 * 60 * 60 * 1000)
                })
            }

            // Initialize session manager for automatic token refresh
            await oidcSessionManager.initialize(provider, async () => {
                console.warn('Token refresh failed, logging out')
                await this.logout()
            })
        } catch (reason: any) {
            this.accessDenied = true
            if (reason) {
                throw new Error(reason)
            } else {
                throw new Error('OIDC authentication failed')
            }
        }
    }

    /**
     * Parse the expiry date from a JWT token
     */
    private parseJwtExpiry(token: string): Date | null {
        try {
            const parts = token.split('.')
            if (parts.length !== 3) return null
            const payload = JSON.parse(atob(parts[1]))
            return payload.exp ? new Date(payload.exp * 1000) : null
        } catch {
            return null
        }
    }

    public async logout(): Promise<void> {
        // Cleanup OIDC session manager first
        await oidcSessionManager.cleanup()

        if (this.connectedInfo) {
            try {
                await Continuum.disconnect()
            } catch (error) {
                debug('Error disconnecting from Continuum: %O', error)
            }
        }

        Cookies.remove('token')
        Cookies.remove('oidc_refresh_token')

        this.connectedInfo = null
        this.oidcUser = null
        this.authenticated = false
        this.accessDenied = false
    }

    public isAccessDenied(): boolean {
        return this.accessDenied
    }

    public isAuthenticated(): boolean {
        // Check if we have an active Continuum connection
        return this.authenticated && this.connectedInfo !== null
    }

    private isValidJWT(token: string): boolean {
        try {
            const parts = token.split('.');
            if (parts.length !== 3) {
                return false;
            }

            const header = JSON.parse(atob(parts[0]));
            const payload = JSON.parse(atob(parts[1]));

            return !!(header.alg && payload.iss && payload.aud);
        } catch (error) {
            return false;
        }
    }
}

export const USER_STATE: IUserState = reactive(new UserState())
