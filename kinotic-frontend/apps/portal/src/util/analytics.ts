import Clarity from '@microsoft/clarity'

/**
 * Google Analytics and Microsoft Clarity for the portal, measuring how visitors who come from the
 * Kinotic website go on to sign up. A build turns each provider on by naming its id in
 * `VITE_GA_MEASUREMENT_ID` or `VITE_CLARITY_PROJECT_ID`; a build that names neither loads no analytics.
 * Both providers start cookieless and grant storage only when the visitor allowed it in the website's
 * cookie banner, which saves the choice in a cookie the portal's subdomain shares.
 */

const GA_MEASUREMENT_ID = import.meta.env.VITE_GA_MEASUREMENT_ID ?? ''
const CLARITY_PROJECT_ID = import.meta.env.VITE_CLARITY_PROJECT_ID ?? ''

// Written by the Kinotic website's cookie banner (website/app/utils/consent.ts) on the parent domain.
const CONSENT_COOKIE = 'kinotic-consent'

/** How a person signs up: with an identity provider such as GitHub, or with an email address and password. */
export type SignUpMethod = 'social' | 'email'

/** A step of the sign-up funnel that the website's `cloud_beta_cta_clicked` event begins. */
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

function initGoogleAnalytics(consent: ConsentChoice): void {
  const marketing = consent.marketing ? 'granted' : 'denied'
  window.dataLayer = window.dataLayer || []
  gtag('consent', 'default', {
    ad_storage: marketing,
    ad_user_data: marketing,
    ad_personalization: marketing,
    analytics_storage: consent.analytics ? 'granted' : 'denied',
  })
  gtag('js', new Date())
  gtag('config', GA_MEASUREMENT_ID)

  const script = document.createElement('script')
  script.async = true
  script.src = `https://www.googletagmanager.com/gtag/js?id=${GA_MEASUREMENT_ID}`
  document.head.appendChild(script)
}

function initClarity(consent: ConsentChoice): void {
  Clarity.init(CLARITY_PROJECT_ID)
  Clarity.consentV2({
    ad_Storage: consent.marketing ? 'granted' : 'denied',
    analytics_Storage: consent.analytics ? 'granted' : 'denied',
  })
}

/** Loads each analytics provider the build names, with the storage the visitor's cookie choice grants. */
export function initAnalytics(): void {
  const consent = readConsent()
  if (GA_MEASUREMENT_ID) initGoogleAnalytics(consent)
  if (CLARITY_PROJECT_ID) initClarity(consent)
}

/** Records a step of the sign-up funnel with each analytics provider the build names. */
export function trackSignUp(event: SignUpEvent, method: SignUpMethod): void {
  try {
    if (GA_MEASUREMENT_ID) gtag('event', event, { method })
    if (CLARITY_PROJECT_ID) {
      Clarity.setTag('signup_method', method)
      Clarity.event(event)
    }
  } catch {
    // analytics must not interrupt the sign-up when a provider is unavailable
  }
}
