import { setClarityConsent } from '~/utils/clarity'
import { setGoogleAnalyticsConsent } from '~/utils/googleAnalytics'

/**
 * The cookie holding the visitor's choice. It is set on the site's own domain and its subdomains, so the
 * Kinotic Cloud portal at portal.kinotic.ai applies the same choice; the portal reads it by this name.
 */
export const CONSENT_COOKIE = 'kinotic-consent'

// The banner asks again once a year.
const CONSENT_MAX_AGE_SECONDS = 365 * 24 * 60 * 60

/** A visitor's choice for each optional cookie category. Necessary storage is always on. */
export interface ConsentChoice {
  analytics: boolean
  marketing: boolean
}

/** The choice in effect before a visitor decides: every optional category off. */
export const NO_CONSENT: ConsentChoice = { analytics: false, marketing: false }

/** Returns the visitor's saved choice, or null when they have not chosen yet. */
export function readConsent(): ConsentChoice | null {
  const prefix = `${CONSENT_COOKIE}=`
  const stored = document.cookie.split('; ').find(cookie => cookie.startsWith(prefix))?.slice(prefix.length)

  let choice: ConsentChoice | null = null
  if (stored) {
    try {
      const parsed = JSON.parse(decodeURIComponent(stored))
      if (typeof parsed?.analytics === 'boolean' && typeof parsed?.marketing === 'boolean') {
        choice = { analytics: parsed.analytics, marketing: parsed.marketing }
      }
    } catch {
      // An unreadable value counts as no choice, so the banner asks again.
    }
  }
  return choice
}

/** Saves the visitor's choice for later visits to the site and the Kinotic Cloud portal. */
export function persistConsent(choice: ConsentChoice): void {
  const { hostname, protocol } = window.location
  // A Domain attribute shares the cookie with subdomains; a single-label host such as localhost cannot take one.
  const domain = hostname.includes('.') ? `; Domain=${hostname}` : ''
  const secure = protocol === 'https:' ? '; Secure' : ''
  document.cookie = `${CONSENT_COOKIE}=${encodeURIComponent(JSON.stringify(choice))}; Path=/; `
    + `Max-Age=${CONSENT_MAX_AGE_SECONDS}; SameSite=Lax${domain}${secure}`
}

/** Grants or revokes each analytics provider's storage to match the visitor's choice. */
export function applyConsent(choice: ConsentChoice): void {
  setClarityConsent(choice)
  setGoogleAnalyticsConsent(choice)
}
