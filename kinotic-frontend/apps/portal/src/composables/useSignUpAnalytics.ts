/**
 * Google Analytics for the sign-up funnel that the Kinotic website's `cloud_beta_cta_clicked` event begins.
 * A build turns it on by naming the portal's Google Analytics data stream in `VITE_GA_MEASUREMENT_ID`; a build
 * that names none loads nothing. The tag loads only on the pages that call this composable, sends no page
 * views, and records the events passed to `trackSignUp` besides Google Analytics' automatic session and
 * engagement events, each reporting the page URL without its query. It takes storage only as the visitor allowed
 * in the website's cookie banner, which saves the choice in a cookie the portal's subdomain shares.
 */

import { onBeforeRouteLeave, useRouter } from 'vue-router'

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

// Google's consent parameters for the visitor's current cookie choice.
function consentParams(): Record<string, 'granted' | 'denied'> {
  const consent = readConsent()
  const marketing = consent.marketing ? 'granted' : 'denied'
  return {
    ad_storage: marketing,
    ad_user_data: marketing,
    ad_personalization: marketing,
    analytics_storage: consent.analytics ? 'granted' : 'denied',
  }
}

// The URL without its query and fragment, which on /signup/verify and /register carry a registration token.
function withoutQuery(url: string): string {
  let ret = ''
  if (url) {
    const parsed = new URL(url)
    ret = `${parsed.origin}${parsed.pathname}`
  }
  return ret
}

// Every hit reports the page by these values rather than the document's own URL.
function setPage(): void {
  gtag('set', { page_location: withoutQuery(window.location.href), page_referrer: withoutQuery(document.referrer) })
}

function loadGoogleAnalytics(): void {
  window.dataLayer = window.dataLayer || []
  gtag('consent', 'default', consentParams())
  gtag('js', new Date())
  setPage()
  // The portal's data stream has Enhanced Measurement off, so with this the tag sends only trackSignUp's events.
  gtag('config', GA_MEASUREMENT_ID, { send_page_view: false })

  const script = document.createElement('script')
  script.async = true
  script.src = `https://www.googletagmanager.com/gtag/js?id=${GA_MEASUREMENT_ID}`
  document.head.appendChild(script)
}

/**
 * Loads the sign-up funnel's analytics for a sign-up page. Call it from the page's setup, so the tag has loaded
 * before a step that leaves the page, such as a provider's sign-up button. When analytics is on, leaving the page
 * for another route loads that route as a new document, so the tag never runs outside the sign-up pages.
 */
export function useSignUpAnalytics() {
  if (GA_MEASUREMENT_ID) {
    if (!loaded) {
      loaded = true
      loadGoogleAnalytics()
    }
    const router = useRouter()
    onBeforeRouteLeave((to) => {
      window.location.assign(router.resolve(to).href)
      return false
    })
  }

  /** Records a step of the sign-up funnel, with how the person is signing up, under the visitor's current cookie choice. */
  const trackSignUp = (event: SignUpEvent, method: SignUpMethod): void => {
    if (!GA_MEASUREMENT_ID) return
    try {
      // the choice can change in a website tab while this page stays open
      gtag('consent', 'update', consentParams())
      setPage()
      gtag('event', event, { method })
    } catch {
      // analytics must not interrupt the sign-up
    }
  }

  return { trackSignUp }
}
