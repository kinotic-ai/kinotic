import Clarity from '@microsoft/clarity'

// IANA timezones for the EU, EEA, and UK (incl. crown dependencies).
// Used as a best-effort proxy for jurisdiction when deciding whether to
// show a cookie consent banner before granting Microsoft Clarity full
// (cookie-based) tracking. EEA + UK + Switzerland are the regions where
// Clarity itself enforces consent signals.
const EU_UK_TIMEZONES = new Set<string>([
  // United Kingdom & crown dependencies
  'Europe/London',
  'Europe/Belfast',
  'Europe/Guernsey',
  'Europe/Isle_of_Man',
  'Europe/Jersey',
  'Europe/Gibraltar',
  // EU member states
  'Europe/Vienna',
  'Europe/Brussels',
  'Europe/Sofia',
  'Europe/Zagreb',
  'Asia/Famagusta',
  'Asia/Nicosia',
  'Europe/Prague',
  'Europe/Copenhagen',
  'Europe/Tallinn',
  'Europe/Helsinki',
  'Europe/Paris',
  'Europe/Berlin',
  'Europe/Busingen',
  'Europe/Athens',
  'Europe/Budapest',
  'Europe/Dublin',
  'Europe/Rome',
  'Europe/Riga',
  'Europe/Vilnius',
  'Europe/Luxembourg',
  'Europe/Malta',
  'Europe/Amsterdam',
  'Europe/Warsaw',
  'Europe/Lisbon',
  'Atlantic/Azores',
  'Atlantic/Madeira',
  'Europe/Bucharest',
  'Europe/Bratislava',
  'Europe/Ljubljana',
  'Europe/Madrid',
  'Africa/Ceuta',
  'Atlantic/Canary',
  'Europe/Stockholm',
  // Other EEA countries + Switzerland + Faroe
  'Atlantic/Reykjavik',
  'Europe/Vaduz',
  'Europe/Oslo',
  'Europe/Zurich',
  'Atlantic/Faroe',
])

export function isEuOrUkVisitor(): boolean {
  if (typeof Intl === 'undefined') return false
  try {
    const tz = Intl.DateTimeFormat().resolvedOptions().timeZone
    return EU_UK_TIMEZONES.has(tz)
  } catch {
    return false
  }
}

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
