import {BearerTokenAuthProvider, ConnectionInfo, createAuthenticatedWebSocketFactory, Kinotic} from '@kinotic-ai/core'
import {confirm} from '@inquirer/prompts'
import open from 'open'
import pTimeout from 'p-timeout'
import {createStateManager} from './state/IStateManager'
import {Logger} from './Logger'

/** OAuth 2.0 token response returned by the device-authorization endpoints. */
interface DeviceTokens {
    access_token: string
    refresh_token: string
    expires_in?: number
}

/** Resolved gateway endpoints for a server url — REST and STOMP share the gateway host/port. */
interface ServerTarget {
    host: string
    port: number
    useSSL: boolean
    restBaseUrl: string
}

/** State key the rotating refresh token is persisted under, keyed by server url. */
const CREDENTIALS_KEY = 'kinotic-credentials'

/** Per-request timeout for REST calls to the Kinotic Server. */
const FETCH_TIMEOUT_MS = 30_000

/** Identifies this CLI to the device grant, which serves only this pre-registered client. */
const CLI_CLIENT_ID = 'kinotic-cli'

/**
 * CLI authentication against a Kinotic server using the OAuth 2.0 Device Authorization Grant
 * (RFC 8628). {@link login} runs the interactive browser flow once and stores the refresh
 * token; {@link connect} uses that stored token to open an authenticated {@link Kinotic}
 * connection, refreshing the short-lived access token before every (re)connect.
 */
export class CliAuthenticator {

    private refreshToken: string | null = null
    private accessToken: string | null = null
    private accessTokenExpiresAt = 0

    /**
     * @param server the server url to authenticate against
     * @param configDir directory the rotating refresh token is persisted in
     * @param logger sink for user-facing progress messages
     */
    constructor(private readonly server: string,
                private readonly configDir: string,
                private readonly logger: Pick<Logger, 'log'>) {}

    /**
     * Runs the interactive device-authorization login and persists the refresh token, so
     * later {@link connect} calls — this run or a future one — are non-interactive.
     *
     * @return true if login succeeded
     */
    public async login(): Promise<boolean> {
        const target = this.parseServer()
        if (target === null) {
            return false
        }
        const tokens = await this.deviceLogin(target.restBaseUrl)
        if (tokens === null) {
            return false
        }
        await this.saveRefreshToken(tokens.refresh_token)
        return true
    }

    /**
     * Opens an authenticated {@link Kinotic} connection using the stored refresh token. Fails
     * fast — with no interactive prompt — when there are no stored credentials.
     *
     * @return true if the connection was established
     */
    public async connect(): Promise<boolean> {
        try {
            const target = this.parseServer()
            if (target === null) {
                return false
            }
            this.refreshToken = await this.loadRefreshToken()
            if (this.refreshToken === null) {
                this.logger.log('Not logged in. Run `kinotic login` first.')
                return false
            }

            const connectionInfo = new ConnectionInfo()
            connectionInfo.host = target.host
            connectionInfo.port = target.port
            connectionInfo.useSSL = target.useSSL
            // The CLI is a Node client: core builds the broker URL and attaches the bearer
            // token as a WebSocket upgrade header. The supplier refreshes the access token
            // before each (re)connect, since core consults the provider on every connect.
            connectionInfo.webSocketFactory = createAuthenticatedWebSocketFactory(
                {host: target.host, port: target.port, useSSL: target.useSSL},
                new BearerTokenAuthProvider(() => this.freshAccessToken(target.restBaseUrl)),
            )

            await pTimeout(Kinotic.connect(connectionInfo), {
                milliseconds: 60000,
                message: 'Connection timeout trying to connect to the Kinotic Server'
            })
            return true
        } catch (e) {
            this.logger.log('Could not connect to the Kinotic Server: '
                            + (e instanceof Error ? e.message : String(e)))
            return false
        }
    }

    /**
     * Returns a valid access token, refreshing it — and persisting the rotated refresh token —
     * when it is absent or within 10s of expiry.
     */
    private async freshAccessToken(restBaseUrl: string): Promise<string> {
        if (this.accessToken !== null && Date.now() < this.accessTokenExpiresAt - 10_000) {
            return this.accessToken
        }
        const res = await fetch(restBaseUrl + '/api/auth/oauth/token', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({grant_type: 'refresh_token', refresh_token: this.refreshToken}),
            signal: AbortSignal.timeout(FETCH_TIMEOUT_MS)
        })
        if (!res.ok) {
            throw new Error('Session expired. Run `kinotic login` again.')
        }
        const tokens = await res.json() as DeviceTokens
        this.refreshToken = tokens.refresh_token
        this.accessToken = tokens.access_token
        this.accessTokenExpiresAt = Date.now() + (tokens.expires_in ?? 60) * 1000
        await this.saveRefreshToken(this.refreshToken)
        return this.accessToken
    }

    /** Parses the server url into the host/port the gateway serves both REST and STOMP on. */
    private parseServer(): ServerTarget | null {
        const url = new URL(this.server)
        if (url.protocol !== 'http:' && url.protocol !== 'https:') {
            this.logger.log('Invalid server URL, only http and https are supported')
            return null
        }
        const useSSL = url.protocol === 'https:'
        // Locally the server url often points at the static web port; the gateway
        // (REST + STOMP) always listens on 58503, so the port is overridden.
        let port: number
        if (url.hostname === 'localhost' || url.hostname === '127.0.0.1') {
            port = 58503
        } else if (url.port) {
            port = Number(url.port)
        } else {
            port = useSSL ? 443 : 58503
        }
        return {
            host: url.hostname,
            port,
            useSSL,
            restBaseUrl: (useSSL ? 'https' : 'http') + '://' + url.hostname + ':' + port
        }
    }

    /** Runs the RFC 8628 device flow: start, browser approval, then poll for tokens. */
    private async deviceLogin(restBaseUrl: string): Promise<DeviceTokens | null> {
        const startRes = await fetch(restBaseUrl + '/api/auth/oauth/device_authorization', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({client_id: CLI_CLIENT_ID}),
            signal: AbortSignal.timeout(FETCH_TIMEOUT_MS)
        })
        if (!startRes.ok) {
            this.logger.log('Could not start device authorization with the Kinotic Server.')
            return null
        }
        const start = await startRes.json() as {
            device_code: string
            user_code: string
            verification_uri_complete: string
            expires_in: number
            interval: number
        }

        this.logger.log('Authenticate your account at:')
        this.logger.log(start.verification_uri_complete)
        this.logger.log(`Your code is: ${start.user_code}`)

        const answer = await confirm({message: 'Open in browser?', default: true})
        if (answer) {
            await open(start.verification_uri_complete)
        }

        const deadline = Date.now() + start.expires_in * 1000
        let intervalMs = Math.max(start.interval, 1) * 1000
        while (Date.now() < deadline) {
            await delay(intervalMs)
            const tokenRes = await fetch(restBaseUrl + '/api/auth/oauth/token', {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: new URLSearchParams({grant_type: 'urn:ietf:params:oauth:grant-type:device_code',
                                           device_code: start.device_code}),
                signal: AbortSignal.timeout(FETCH_TIMEOUT_MS)
            })
            if (tokenRes.ok) {
                return await tokenRes.json() as DeviceTokens
            }
            const error = await readErrorCode(tokenRes)
            if (error === 'slow_down') {
                intervalMs += 5000
            } else if (error !== 'authorization_pending') {
                this.logger.log(`Device authorization failed: ${error}`)
                return null
            }
        }
        this.logger.log('Device authorization timed out before it was approved.')
        return null
    }

    private async loadRefreshToken(): Promise<string | null> {
        const stateManager = createStateManager(this.configDir)
        if (!(await stateManager.containsState(CREDENTIALS_KEY))) {
            return null
        }
        const credentials = await stateManager.load<Record<string, string>>(CREDENTIALS_KEY)
        return credentials[this.server] ?? null
    }

    private async saveRefreshToken(refreshToken: string): Promise<void> {
        const stateManager = createStateManager(this.configDir)
        let credentials: Record<string, string> = {}
        if (await stateManager.containsState(CREDENTIALS_KEY)) {
            credentials = await stateManager.load<Record<string, string>>(CREDENTIALS_KEY)
        }
        credentials[this.server] = refreshToken
        await stateManager.save(CREDENTIALS_KEY, credentials)
    }
}

async function readErrorCode(res: Response): Promise<string> {
    try {
        const body = await res.json() as {error?: string}
        return body.error ?? 'unknown_error'
    } catch {
        return 'unknown_error'
    }
}

function delay(ms: number): Promise<void> {
    return new Promise<void>(resolve => setTimeout(resolve, ms))
}
