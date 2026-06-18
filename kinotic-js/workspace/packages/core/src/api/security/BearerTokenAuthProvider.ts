import type {IAuthProvider} from '@/api/security/IAuthProvider'

/**
 * {@link IAuthProvider} that authenticates with a bearer token, sending
 * `Authorization: Bearer <token>` on every (re)connect.
 *
 * Pass a string for a static token, or a supplier when the token is short-lived
 * and must be refreshed before each connect (for example, an OAuth device-grant
 * access token). The supplier may be async and is invoked on every (re)connect.
 */
export class BearerTokenAuthProvider implements IAuthProvider {

    private readonly token: string | (() => Promise<string> | string)

    constructor(token: string | (() => Promise<string> | string)) {
        this.token = token
    }

    public async getAuthHeaders(): Promise<Record<string, string>> {
        const token: string = typeof this.token === 'function' ? await this.token() : this.token
        return {Authorization: `Bearer ${token}`}
    }
}
