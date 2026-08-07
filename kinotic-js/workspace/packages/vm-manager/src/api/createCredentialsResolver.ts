import type { CredentialsResolver } from '@kinotic-ai/core'
import { BasicCredentialsResolver, BearerCredentialsResolver } from '@kinotic-ai/core'
import type { VmManagerConfig } from '@/api/VmManagerConfig'

/**
 * Resolves the {@link CredentialsResolver} selected by {@link VmManagerConfig#authType}:
 *  - `basic` (default): uses serverLogin / serverPasscode
 *  - `bearer`: uses serverToken
 *
 * @return the configured credentials resolver
 * @throws if the selected mechanism is unknown or a required value is missing
 */
export function createCredentialsResolver(config: VmManagerConfig): CredentialsResolver {
    switch (config.authType) {
        case 'basic':
            return new BasicCredentialsResolver(config.serverLogin, config.serverPasscode)
        case 'bearer': {
            if (!config.serverToken) {
                throw new Error('KINOTIC_SERVER_TOKEN is required when KINOTIC_AUTH_TYPE is "bearer"')
            }
            return new BearerCredentialsResolver(config.serverToken)
        }
        default:
            throw new Error(`Unsupported KINOTIC_AUTH_TYPE: "${config.authType}" (expected "basic" or "bearer")`)
    }
}
