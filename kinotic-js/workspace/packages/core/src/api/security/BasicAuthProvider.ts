import type {IAuthProvider} from '@/api/security/IAuthProvider'

/**
 * {@link IAuthProvider} that authenticates with HTTP Basic, sending
 * `Authorization: Basic base64(login:passcode)` on every (re)connect.
 */
export class BasicAuthProvider implements IAuthProvider {

    private readonly authHeader: string

    constructor(login: string, passcode: string) {
        const encoded: string = Buffer.from(`${login}:${passcode}`).toString('base64')
        this.authHeader = `Basic ${encoded}`
    }

    public getAuthHeaders(): Record<string, string> {
        return {Authorization: this.authHeader}
    }
}
