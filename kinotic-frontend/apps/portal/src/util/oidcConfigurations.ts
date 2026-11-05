import { apiUrl } from '@kinotic-ai/frontend-common'
import { OidcConfigurationType, OidcProviderType, type OwnedOidcConfiguration, type PasswordConfiguration } from '@kinotic-ai/management-api'
import githubLogo from '@/assets/github-icon.svg'
import googleLogo from '@/assets/google-icon.svg'
import microsoftLogo from '@/assets/microsoft_online-icon.svg'
import oktaLogo from '@/assets/okta-icon.svg'

/** The logo each provider kind the portal offers a sign-in through is shown with. */
export const PROVIDER_LOGOS: Partial<Record<OidcProviderType, string>> = {
    [OidcProviderType.GOOGLE]: googleLogo,
    [OidcProviderType.AZURE_AD]: microsoftLogo,
    [OidcProviderType.OKTA]: oktaLogo,
    [OidcProviderType.GITHUB]: githubLogo
}

/** The name a provider kind is shown by, on its sign-in button and wherever the portal lists it. */
export function providerDisplayName(provider: string): string {
    switch (provider) {
        case 'azure-ad': return 'Microsoft'
        case 'github':   return 'GitHub'
        case 'google':   return 'Google'
        case 'okta':     return 'Okta'
        default:         return provider.split('-').map(s => s.charAt(0).toUpperCase() + s.slice(1)).join(' ')
    }
}

/**
 * The message for a sign-in refused with `email_not_verified`, naming the provider the error redirect's `provider`
 * parameter gives by its key, or the person's identity provider when the redirect names none.
 */
export function emailNotVerifiedMessage(provider: string | null | undefined): string {
    const name = provider ? providerDisplayName(provider) : null
    return name
        ? `${name} has not verified your email address. Verify it with ${name} before signing in to Kinotic.`
        : 'Your identity provider has not verified your email address. Verify it there before signing in to Kinotic.'
}

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
