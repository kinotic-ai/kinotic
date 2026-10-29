import Clarity from '@microsoft/clarity'
import type { ConsentChoice } from '~/utils/consent'

let clarityInitialized = false
let analyticsConsentGranted = false

export function initClarity(projectId: string): void {
  if (clarityInitialized || !projectId || typeof window === 'undefined') return
  clarityInitialized = true
  Clarity.init(projectId)
}

/** Grants or revokes Clarity's analytics storage and its link to Microsoft Advertising. */
export function setClarityConsent(choice: ConsentChoice): void {
  analyticsConsentGranted = choice.analytics
  if (!clarityInitialized) return
  Clarity.consentV2({
    ad_Storage: choice.marketing ? 'granted' : 'denied',
    analytics_Storage: choice.analytics ? 'granted' : 'denied',
  })
}

/** Records a website interaction when the visitor has allowed Analytics. */
export function trackClarityEvent(name: string): void {
  if (!clarityInitialized || !analyticsConsentGranted) return
  try {
    Clarity.event(name)
  } catch {
    // Analytics must not interrupt the visitor's action when the provider is unavailable.
  }
}
