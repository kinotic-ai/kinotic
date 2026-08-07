import type {CredentialsResolver, ResolvedCredentials, ServerInfo} from '@kinotic-ai/core'
import {createStateManager} from './state/IStateManager'

/** OAuth 2.0 token response returned by the server's token endpoint. */
export interface TokenResponse {
    access_token: string
    refresh_token: string
    expires_in?: number
}

/** State key the rotating refresh token is persisted under, keyed by server url. */
const CREDENTIALS_KEY = 'kinotic-credentials'

/** Per-request timeout for REST calls to the Kinotic Server. */
export const FETCH_TIMEOUT_MS = 30_000

/** Returns the refresh token `kinotic login` stored for the server url, or null when there is none. */
export async function loadRefreshToken(configDir: string, serverUrl: string): Promise<string | null> {
    let ret: string | null = null
    const stateManager = createStateManager(configDir)
    if (await stateManager.containsState(CREDENTIALS_KEY)) {
        const credentials = await stateManager.load<Record<string, string>>(CREDENTIALS_KEY)
        ret = credentials[serverUrl] ?? null
    }
    return ret
}

/** Persists the refresh token for the server url, preserving tokens stored for other servers. */
export async function saveRefreshToken(configDir: string, serverUrl: string, refreshToken: string): Promise<void> {
    const stateManager = createStateManager(configDir)
    let credentials: Record<string, string> = {}
    if (await stateManager.containsState(CREDENTIALS_KEY)) {
        credentials = await stateManager.load<Record<string, string>>(CREDENTIALS_KEY)
    }
    credentials[serverUrl] = refreshToken
    await stateManager.save(CREDENTIALS_KEY, credentials)
}

/**
 * Resolves the bearer token for a CLI connection from the refresh token `kinotic login`
 * stored for the server. The access token is cached until shortly before expiry and
 * refreshed through the server's OAuth token endpoint after that; each refresh rotates the
 * refresh token, which is persisted so the login outlives this process. Resolves null when
 * no login is stored for the server.
 */
export class CliLoginCredentialsResolver implements CredentialsResolver {

    public readonly name = 'CliLogin'

    private refreshToken: string | null = null
    private accessToken: string | null = null
    private accessTokenExpiresAt = 0

    /**
     * @param serverUrl the configured server url the stored refresh token is keyed by
     * @param configDir directory the rotating refresh token is persisted in
     */
    constructor(private readonly serverUrl: string,
                private readonly configDir: string) {}

    public async resolve(server: ServerInfo): Promise<ResolvedCredentials | null> {
        let ret: ResolvedCredentials | null = null
        if (this.refreshToken === null) {
            this.refreshToken = await loadRefreshToken(this.configDir, this.serverUrl)
        }
        if (this.refreshToken !== null) {
            // the gateway serves REST on the same host/port the connection targets
            const restBaseUrl = (server.useSSL ? 'https' : 'http') + '://' + server.host + ':' + server.port
            ret = {authHeaders: {Authorization: 'Bearer ' + await this.freshAccessToken(restBaseUrl)}}
        }
        return ret
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
            body: new URLSearchParams({grant_type: 'refresh_token', refresh_token: this.refreshToken as string}),
            signal: AbortSignal.timeout(FETCH_TIMEOUT_MS)
        })
        if (!res.ok) {
            throw new Error('Session expired. Run `kinotic login` again.')
        }
        const tokens = await res.json() as TokenResponse
        this.refreshToken = tokens.refresh_token
        this.accessToken = tokens.access_token
        this.accessTokenExpiresAt = Date.now() + (tokens.expires_in ?? 60) * 1000
        await saveRefreshToken(this.configDir, this.serverUrl, this.refreshToken)
        return this.accessToken
    }
}
