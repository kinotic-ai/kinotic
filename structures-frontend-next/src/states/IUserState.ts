import { ConnectedInfo, ConnectionInfo, Continuum } from '@mindignited/continuum-client'
import { reactive } from 'vue'
import Cookies from 'js-cookie'
import { User } from 'oidc-client-ts'
import { createDebug } from '@/util/debug'

const debug = createDebug('user-state');

export interface IUserState {
    connectedInfo: ConnectedInfo | null
    oidcUser: User | null

    isAccessDenied(): boolean
    isAuthenticated(): boolean
    authenticate(login: string, passcode: string): Promise<void>
    handleOidcLogin(user: User): Promise<void>
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

        const connectionInfo: ConnectionInfo = this.createConnectionInfo()
        connectionInfo.connectHeaders = {
            login,
            passcode
        }

        const btoaToken = btoa(`${login}:${passcode}`)

        try {
            this.connectedInfo = await Continuum.connect(connectionInfo)
            this.authenticated = true
            this.accessDenied = false

            const useSecureCookies = window.location.protocol === 'https:'

            Cookies.set('token', btoaToken, {
                sameSite: 'strict',
                secure: useSecureCookies,
                expires: 1
            })
        } catch (reason: any) {
            this.accessDenied = true
            if (reason) {
                throw new Error(reason)
            } else {
                throw new Error('Credentials invalid')
            }
        }
    }

    public async handleOidcLogin(user: User): Promise<void> {
        try {
            await Continuum.disconnect()
        } catch (error) {
            debug('No existing connection to disconnect')
        }

        const connectionInfo: ConnectionInfo = this.createConnectionInfo()

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
                Cookies.set('oidc_refresh_token', user.refresh_token, {
                    sameSite: 'strict',
                    secure: useSecureCookies,
                    expires: 30
                })
            }
        } catch (reason: any) {
            this.accessDenied = true
            if (reason) {
                throw new Error(reason)
            } else {
                throw new Error('OIDC authentication failed')
            }
        }
    }

    public async logout(): Promise<void> {
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
        return this.authenticated && (
            Cookies.get('token') !== undefined ||
            (this.oidcUser !== null &&
                this.oidcUser.expires_at !== undefined &&
                this.oidcUser.expires_at * 1000 > Date.now())
        )
    }

    public createConnectionInfo(): ConnectionInfo {
        // Use build time variable if available, otherwise use default
        const envPort = import.meta.env.VITE_CONTINUUM_PORT ? parseInt(import.meta.env.VITE_CONTINUUM_PORT) : 58503

        const connectionInfo: ConnectionInfo = {
            host: 'localhost',
            port: envPort 
        }

        if (window.location.protocol.startsWith('https')) {
            connectionInfo.useSSL = true
        }

        // Auto-detect from window location if not localhost
        if (window.location.hostname !== '127.0.0.1'
            && window.location.hostname !== 'localhost') {

            connectionInfo.host = window.location.hostname

        }

        // we are using ssl and no port is in use so we assume a proxy
        // is in use and we default to 443
        if (connectionInfo.useSSL
            && window.location.port === '') {
            connectionInfo.port = 443
        }
        return connectionInfo
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
