import type {IAuthProvider} from '@/api/security/IAuthProvider'

/**
 * {@link IAuthProvider} that authenticates with HTTP Basic, sending
 * `Authorization: Basic base64(login:passcode)` on every (re)connect.
 */
export class BasicAuthProvider implements IAuthProvider {

    private readonly authHeader: string

    constructor(login: string, passcode: string) {
        // btoa only handles Latin1, so encode the credentials as UTF-8 bytes before base64
        const bytes = new TextEncoder().encode(`${login}:${passcode}`)
        let binary = ''
        for (const byte of bytes) {
            binary += String.fromCharCode(byte)
        }
        this.authHeader = `Basic ${btoa(binary)}`
    }

    public getAuthHeaders(): Record<string, string> {
        return {Authorization: this.authHeader}
    }
}
