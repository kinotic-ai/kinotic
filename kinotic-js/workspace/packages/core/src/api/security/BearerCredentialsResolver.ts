import type {ServerInfo} from '@/api/ConnectOptions'
import type {CredentialsResolver, ResolvedCredentials} from '@/api/security/CredentialsResolver'

/**
 * A bearer token presented as the {@code Authorization} upgrade header. The supplier form is
 * invoked on every (re)connect, so short-lived tokens can be refreshed each time.
 */
export class BearerCredentialsResolver implements CredentialsResolver {

    public readonly name: string = 'BearerCredentialsResolver'
    private readonly token: string | (() => Promise<string> | string)

    constructor(token: string | (() => Promise<string> | string)) {
        this.token = token
    }

    public async resolve(_server: ServerInfo): Promise<ResolvedCredentials> {
        const token = typeof this.token === 'function' ? await this.token() : this.token
        return {authHeaders: {Authorization: `Bearer ${token}`}}
    }
}
