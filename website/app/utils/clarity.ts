// IANA timezones for the EU, EEA, and UK (incl. crown dependencies).
// Used as a best-effort proxy for jurisdiction when deciding whether to
// show a cookie consent banner before loading Microsoft Clarity.
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
  // Other EEA countries (Iceland, Liechtenstein, Norway) + Faroe
  'Atlantic/Reykjavik',
  'Europe/Vaduz',
  'Europe/Oslo',
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

let clarityLoaded = false

export function loadClarity(projectId: string): void {
  if (clarityLoaded || !projectId || typeof window === 'undefined') return
  clarityLoaded = true

  // Standard Microsoft Clarity tag
  ;(function (c: any, l: Document, a: string, r: string, i: string) {
    c[a] = c[a] || function (...args: unknown[]) {
      (c[a].q = c[a].q || []).push(args)
    }
    const t = l.createElement(r) as HTMLScriptElement
    t.async = true
    t.src = 'https://www.clarity.ms/tag/' + i
    const y = l.getElementsByTagName(r)[0]
    y?.parentNode?.insertBefore(t, y)
  })(window, document, 'clarity', 'script', projectId)
}
