import { apiUrl } from '@kinotic-ai/frontend-common'
import { OidcConfigurationType, OidcProviderType, type OwnedOidcConfiguration } from '@kinotic-ai/management-api'
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
