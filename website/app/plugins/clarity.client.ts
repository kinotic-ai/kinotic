import { createApp, h } from 'vue'
import CookieConsent from '~/components/CookieConsent.vue'
import { initClarity, setClarityConsentV2 } from '~/utils/clarity'

const STORAGE_KEY = 'kinotic-clarity-consent'

type StoredConsent = 'accepted' | 'declined'

function readStored(): StoredConsent | null {
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    return v === 'accepted' || v === 'declined' ? v : null
  } catch {
    return null
  }
}

function persist(value: StoredConsent): void {
  try {
    localStorage.setItem(STORAGE_KEY, value)
  } catch {
    // ignore (private mode, etc.)
  }
}

function mountBanner(onDecision: (accepted: boolean) => void): void {
  const container = document.createElement('div')
  container.setAttribute('data-cookie-consent', '')
  document.body.appendChild(container)

  const app = createApp({
    setup() {
      const handle = (accepted: boolean) => {
        onDecision(accepted)
        app.unmount()
        container.remove()
      }
      return () => h(CookieConsent, { onDecision: handle })
    },
  })
  app.mount(container)
}

export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig()
  const projectId = (config.public.clarityProjectId as string | undefined) || ''
  if (!projectId) return

  // Always initialize Clarity. With Consent Mode enabled in the Clarity
  // project settings, Clarity starts in denied/cookieless mode and only
  // upgrades when we call consentV2 with granted values.
  initClarity(projectId)

  const stored = readStored()

  if (stored === 'accepted') {
    setClarityConsentV2('granted', 'granted')
    return
  }
  if (stored === 'declined') {
    setClarityConsentV2('denied', 'denied')
    return
  }

  // No prior choice: stay cookieless and ask the visitor.
  setClarityConsentV2('denied', 'denied')
  mountBanner((accepted) => {
    if (accepted) {
      persist('accepted')
      setClarityConsentV2('granted', 'granted')
    } else {
      persist('declined')
      setClarityConsentV2('denied', 'denied')
    }
  })
})
