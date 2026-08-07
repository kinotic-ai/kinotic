import {buildServerUrl, type CredentialsResolver, type ResolvedCredentials, type ServerInfo} from '@kinotic-ai/core'
import {createStateManager, type IStateManager} from './state/IStateManager'

/** OAuth 2.0 token response returned by the server's token endpoint. */
export interface TokenResponse {
    access_token: string
    refresh_token: string
    expires_in?: number
}

/** Path of the server's OAuth token endpoint, shared by the device grant and the refresh grant. */
export const OAUTH_TOKEN_PATH = '/api/auth/oauth/token'

/** Per-request timeout for REST calls to the Kinotic Server. */
const FETCH_TIMEOUT_MS = 30_000

/** POSTs an application/x-www-form-urlencoded body — the shape of every OAuth endpoint call. */
export function postForm(url: string, params: Record<string, string>): Promise<Response> {
    return fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams(params),
        signal: AbortSignal.timeout(FETCH_TIMEOUT_MS)
    })
}

/**
 * Resolves the bearer token for a CLI connection from the refresh token `kinotic login`
 * stored for the server. The access token is cached until shortly before expiry and
 * refreshed through the server's OAuth token endpoint after that; each refresh rotates the
 * refresh token, which is persisted so the login outlives this process. Resolves null when
 * no login is stored for the server.
 */
export class CliLoginCredentialsResolver implements CredentialsResolver {

    /** State key the rotating refresh token is persisted under, keyed by server url. */
    private static readonly CREDENTIALS_KEY = 'kinotic-credentials'

    public readonly name = 'CliLogin'

    private readonly stateManager: IStateManager
    private refreshToken: string | null = null
    private accessToken: string | null = null
    private accessTokenExpiresAt = 0

    /**
     * @param serverUrl the configured server url the stored refresh token is keyed by
     * @param configDir directory the rotating refresh token is persisted in
     */
    constructor(private readonly serverUrl: string,
                configDir: string) {
        this.stateManager = createStateManager(configDir)
    }

    public async resolve(server: ServerInfo): Promise<ResolvedCredentials | null> {
        let ret: ResolvedCredentials | null = null
        if (this.refreshToken === null) {
            this.refreshToken = (await this.loadCredentialMap())[this.serverUrl] ?? null
        }
        if (this.refreshToken !== null) {
            // the gateway serves REST on the same host/port the connection targets
            ret = {authHeaders: {Authorization: 'Bearer ' + await this.freshAccessToken(buildServerUrl(server, 'http'))}}
        }
        return ret
    }

    /**
     * Persists a refresh token for this resolver's server — the hand-off from
     * `kinotic login` — preserving the tokens stored for other servers.
     */
    public async storeRefreshToken(refreshToken: string): Promise<void> {
        const credentials = await this.loadCredentialMap()
        credentials[this.serverUrl] = refreshToken
        await this.stateManager.save(CliLoginCredentialsResolver.CREDENTIALS_KEY, credentials)
    }

    private loadCredentialMap(): Promise<Record<string, string>> {
        return this.stateManager.loadOrDefault<Record<string, string>>(CliLoginCredentialsResolver.CREDENTIALS_KEY, {})
    }

    /**
     * Returns a valid access token, refreshing it — and persisting the rotated refresh token —
     * when it is absent or within 10s of expiry.
     */
    private async freshAccessToken(baseUrl: string): Promise<string> {
        if (this.accessToken !== null && Date.now() < this.accessTokenExpiresAt - 10_000) {
            return this.accessToken
        }
        const res = await postForm(baseUrl + OAUTH_TOKEN_PATH,
                                   {grant_type: 'refresh_token', refresh_token: this.refreshToken as string})
        if (!res.ok) {
            throw new Error('Session expired. Run `kinotic login` again.')
        }
        const tokens = await res.json() as TokenResponse
        this.refreshToken = tokens.refresh_token
        this.accessToken = tokens.access_token
        this.accessTokenExpiresAt = Date.now() + (tokens.expires_in ?? 60) * 1000
        await this.storeRefreshToken(this.refreshToken)
        return this.accessToken
    }
}
