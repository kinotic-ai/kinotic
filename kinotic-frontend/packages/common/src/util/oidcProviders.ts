import githubLogo from '../assets/github-icon.svg'
import googleLogo from '../assets/google-icon.svg'
import microsoftLogo from '../assets/microsoft_online-icon.svg'
import oktaLogo from '../assets/okta-icon.svg'

/** The logo each provider kind a sign-in is offered through is shown with, by the provider's key. */
export const PROVIDER_LOGOS: Partial<Record<string, string>> = {
    'google': googleLogo,
    'azure-ad': microsoftLogo,
    'okta': oktaLogo,
    'github': githubLogo
}

/** The name a provider kind is shown by, on its sign-in button and wherever it is listed. */
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
