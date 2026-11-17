import { apiUrl } from '@kinotic-ai/frontend-common'
import { OidcConfigurationType, type OwnedOidcConfiguration, type PasswordConfiguration } from '@kinotic-ai/management-api'

/** The name an email and a password is shown by among a scope's sign-in providers. */
export const PASSWORD_PROVIDER_NAME = 'Email and password'

/** One of an organization's sign-in providers: an OIDC configuration, or the email and password of one login. */
export type SignInProvider =
    | { kind: 'oidc', configuration: OwnedOidcConfiguration }
    | { kind: 'password', configuration: PasswordConfiguration }

export const START_URL_HELP = 'Opens a sign-in through this provider directly, for a tile in the provider\'s application launcher.'

/**
 * The link that starts a sign-in to the organization through `configuration`, which a provider's application
 * launcher can open; null for a configuration of an application or a tenant, whose login is the application's own.
 */
export function startUrlOf(configuration: OwnedOidcConfiguration): string | null {
    return configuration.type === OidcConfigurationType.ORGANIZATION && configuration.id && configuration.organizationId
        ? apiUrl(`/api/auth/org/login/${configuration.organizationId}/start/${configuration.id}`)
        : null
}
