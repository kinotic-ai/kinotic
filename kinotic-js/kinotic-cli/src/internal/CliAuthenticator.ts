import {Kinotic} from '@kinotic-ai/core'
import {ensureNodeWebSocket} from '@kinotic-ai/core/node'
import {confirm} from '@inquirer/prompts'
import open from 'open'
import pTimeout from 'p-timeout'
import {CliLoginCredentialsResolver, type TokenResponse} from './CliLoginCredentialsResolver'
import {Logger} from './Logger'

/** Resolved gateway endpoints for a server url — REST and STOMP share the gateway host/port. */
interface ServerTarget {
    host: string
    port: number
    useSSL: boolean
    restBaseUrl: string
}

/** Per-request timeout for REST calls to the Kinotic Server. */
const FETCH_TIMEOUT_MS = 30_000

/** Identifies this CLI to the device grant, which serves only this pre-registered client. */
const CLI_CLIENT_ID = 'kinotic-cli'

/**
 * CLI authentication against a Kinotic server using the OAuth 2.0 Device Authorization Grant
 * (RFC 8628). {@link login} runs the interactive browser flow once and stores the refresh
 * token; {@link connect} opens a {@link Kinotic} connection authenticated by a
 * {@link CliLoginCredentialsResolver} backed by that stored token.
 */
export class CliAuthenticator {

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
        await new CliLoginCredentialsResolver(this.server, this.configDir).storeRefreshToken(tokens.refresh_token)
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
            const resolver = new CliLoginCredentialsResolver(this.server, this.configDir)
            // Resolved once up front so a missing login gets its friendly message instead of
            // the generic chain failure; the resolver caches the token for the connect below.
            if (await resolver.resolve(target) === null) {
                this.logger.log('Not logged in. Run `kinotic login` first.')
                return false
            }
            // The resolver's bearer token rides the WebSocket upgrade headers, which needs
            // the header-capable ws WebSocket installed in a Node process.
            ensureNodeWebSocket()
            await pTimeout(Kinotic.connect({
                host: target.host,
                port: target.port,
                useSSL: target.useSSL,
                credentials: resolver
            }), {
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
    private async deviceLogin(restBaseUrl: string): Promise<TokenResponse | null> {
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
                return await tokenRes.json() as TokenResponse
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
