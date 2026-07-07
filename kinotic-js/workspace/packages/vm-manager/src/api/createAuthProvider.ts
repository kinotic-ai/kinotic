import type { IAuthProvider } from '@kinotic-ai/core'
import { BasicAuthProvider, BearerTokenAuthProvider } from '@kinotic-ai/core'
import type { VmManagerConfig } from '@/api/VmManagerConfig'

/**
 * Resolves the {@link IAuthProvider} selected by {@link VmManagerConfig#authType}:
 *  - `basic` (default): uses serverLogin / serverPasscode
 *  - `bearer`: uses serverToken
 *
 * @return the configured auth provider
 * @throws if the selected mechanism is unknown or a required value is missing
 */
export function createAuthProvider(config: VmManagerConfig): IAuthProvider {
    switch (config.authType) {
        case 'basic':
            return new BasicAuthProvider(config.serverLogin, config.serverPasscode)
        case 'bearer': {
            if (!config.serverToken) {
                throw new Error('KINOTIC_SERVER_TOKEN is required when KINOTIC_AUTH_TYPE is "bearer"')
            }
            return new BearerTokenAuthProvider(config.serverToken)
        }
        default:
            throw new Error(`Unsupported KINOTIC_AUTH_TYPE: "${config.authType}" (expected "basic" or "bearer")`)
    }
}
