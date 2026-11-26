/**
 * Google Analytics for the sign-up funnel that the Kinotic website's `cloud_beta_cta_clicked` event begins.
 * A build turns it on by naming the portal's Google Analytics data stream in `VITE_GA_MEASUREMENT_ID`; a build
 * that names none loads nothing. The tag loads only on the pages that call this composable, sends no page
 * views, and records only the events passed to `trackSignUp`. It takes storage only as the visitor allowed
 * in the website's cookie banner, which saves the choice in a cookie the portal's subdomain shares.
 */

const GA_MEASUREMENT_ID = import.meta.env.VITE_GA_MEASUREMENT_ID ?? ''

// Written by the Kinotic website's cookie banner (website/app/utils/consent.ts) on the parent domain.
const CONSENT_COOKIE = 'kinotic-consent'

/** How a person signs up: with an identity provider such as GitHub, or with an email address and password. */
export type SignUpMethod = 'social' | 'email'

/** A step of the sign-up funnel. */
export type SignUpEvent = 'cloud_signup_started' | 'cloud_signup_completed'

interface ConsentChoice {
  analytics: boolean
  marketing: boolean
}

declare global {
  interface Window {
    dataLayer: unknown[]
  }
}

let loaded = false

// gtag.js only processes Arguments objects from the data layer, so this pushes `arguments`.
function gtag(..._args: unknown[]): void {
  // eslint-disable-next-line prefer-rest-params
  window.dataLayer.push(arguments)
}

function readConsent(): ConsentChoice {
  const prefix = `${CONSENT_COOKIE}=`
  const stored = document.cookie.split('; ').find(cookie => cookie.startsWith(prefix))?.slice(prefix.length)

  let choice: ConsentChoice = { analytics: false, marketing: false }
  if (stored) {
    try {
      const parsed = JSON.parse(decodeURIComponent(stored))
      if (typeof parsed?.analytics === 'boolean' && typeof parsed?.marketing === 'boolean') {
        choice = { analytics: parsed.analytics, marketing: parsed.marketing }
      }
    } catch {
      // an unreadable value grants nothing
    }
  }
  return choice
}

function loadGoogleAnalytics(): void {
  const consent = readConsent()
  const marketing = consent.marketing ? 'granted' : 'denied'
  window.dataLayer = window.dataLayer || []
  gtag('consent', 'default', {
    ad_storage: marketing,
    ad_user_data: marketing,
    ad_personalization: marketing,
    analytics_storage: consent.analytics ? 'granted' : 'denied',
  })
  gtag('js', new Date())
  // The portal's data stream has Enhanced Measurement off, so with this the tag sends only trackSignUp's events.
  gtag('config', GA_MEASUREMENT_ID, { send_page_view: false })

  const script = document.createElement('script')
  script.async = true
  script.src = `https://www.googletagmanager.com/gtag/js?id=${GA_MEASUREMENT_ID}`
  document.head.appendChild(script)
}

/**
 * Loads the sign-up funnel's analytics for a sign-up page. Call it from the page's setup, so the tag has loaded
 * before a step that leaves the page, such as a provider's sign-up button.
 */
export function useSignUpAnalytics() {
  if (GA_MEASUREMENT_ID && !loaded) {
    loaded = true
    loadGoogleAnalytics()
  }

  /** Records a step of the sign-up funnel, with how the person is signing up. */
  const trackSignUp = (event: SignUpEvent, method: SignUpMethod): void => {
    if (!GA_MEASUREMENT_ID) return
    try {
      gtag('event', event, { method })
    } catch {
      // analytics must not interrupt the sign-up
    }
  }

  return { trackSignUp }
}
