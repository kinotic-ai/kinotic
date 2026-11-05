import type { ConsentChoice } from '~/utils/consent'

const MEASUREMENT_ID = 'G-MMMX3G571K'

declare global {
  interface Window {
    dataLayer: unknown[]
  }
}

let googleAnalyticsInitialized = false
let analyticsConsentGranted = false

// gtag.js only processes Arguments objects from the data layer, so this pushes
// `arguments` rather than the rest array.
function gtag(..._args: unknown[]): void {
  // eslint-disable-next-line prefer-rest-params
  window.dataLayer.push(arguments)
}

/**
 * Loads the Google tag with every storage type denied, so it sends cookieless
 * pings until {@link setGoogleAnalyticsConsent} grants storage.
 */
export function initGoogleAnalytics(): void {
  if (googleAnalyticsInitialized || typeof window === 'undefined') return
  googleAnalyticsInitialized = true

  window.dataLayer = window.dataLayer || []
  gtag('consent', 'default', {
    ad_storage: 'denied',
    ad_user_data: 'denied',
    ad_personalization: 'denied',
    analytics_storage: 'denied',
  })
  gtag('js', new Date())
  gtag('config', MEASUREMENT_ID)

  const script = document.createElement('script')
  script.async = true
  script.src = `https://www.googletagmanager.com/gtag/js?id=${MEASUREMENT_ID}`
  document.head.appendChild(script)
}

/** Grants or revokes analytics storage, and advertising storage, ad user data and ad personalization. */
export function setGoogleAnalyticsConsent(choice: ConsentChoice): void {
  analyticsConsentGranted = choice.analytics
  if (!googleAnalyticsInitialized) return
  const marketing = choice.marketing ? 'granted' : 'denied'
  gtag('consent', 'update', {
    ad_storage: marketing,
    ad_user_data: marketing,
    ad_personalization: marketing,
    analytics_storage: choice.analytics ? 'granted' : 'denied',
  })
}

/** Records a website interaction when the visitor has allowed Analytics. */
export function trackGoogleAnalyticsEvent(name: string): void {
  if (!googleAnalyticsInitialized || !analyticsConsentGranted) return
  try {
    gtag('event', name)
  } catch {
    // Analytics must not interrupt the visitor's action when the provider is unavailable.
  }
}
