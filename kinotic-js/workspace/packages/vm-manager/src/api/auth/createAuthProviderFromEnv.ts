import type { IAuthProvider } from '@kinotic-ai/core'
import { BasicAuthProvider, BearerTokenAuthProvider } from '@kinotic-ai/core'

/**
 * Resolves an {@link IAuthProvider} from environment variables.
 *
 * `KINOTIC_AUTH_TYPE` selects the authentication mechanism:
 *  - `basic` (default): uses `KINOTIC_SERVER_LOGIN` / `KINOTIC_SERVER_PASSCODE`
 *  - `bearer`: uses `KINOTIC_SERVER_TOKEN`
 *
 * @return the configured auth provider
 * @throws if the selected mechanism is unknown or required variables are missing
 */
export function createAuthProviderFromEnv(): IAuthProvider {
    const authType = (process.env.KINOTIC_AUTH_TYPE ?? 'basic').toLowerCase()

    switch (authType) {
        case 'basic': {
            const login = process.env.KINOTIC_SERVER_LOGIN ?? 'kinotic'
            const passcode = process.env.KINOTIC_SERVER_PASSCODE ?? 'kinotic'
            return new BasicAuthProvider(login, passcode)
        }
        case 'bearer': {
            const token = process.env.KINOTIC_SERVER_TOKEN
            if (!token) {
                throw new Error('KINOTIC_SERVER_TOKEN is required when KINOTIC_AUTH_TYPE is "bearer"')
            }
            return new BearerTokenAuthProvider(token)
        }
        default:
            throw new Error(`Unsupported KINOTIC_AUTH_TYPE: "${authType}" (expected "basic" or "bearer")`)
    }
}
