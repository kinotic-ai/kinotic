import Clarity from '@microsoft/clarity'

let clarityInitialized = false

export function initClarity(projectId: string): void {
  if (clarityInitialized || !projectId || typeof window === 'undefined') return
  clarityInitialized = true
  Clarity.init(projectId)
}

export type ClarityConsent = 'granted' | 'denied'

export function setClarityConsentV2(
  adStorage: ClarityConsent,
  analyticsStorage: ClarityConsent,
): void {
  if (typeof window === 'undefined') return
  Clarity.consentV2({
    ad_Storage: adStorage,
    analytics_Storage: analyticsStorage,
  })
}
