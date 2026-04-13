import { createApp, h } from 'vue'
import CookieConsent from '~/components/CookieConsent.vue'
import { initClarity, isEuOrUkVisitor, setClarityConsent } from '~/utils/clarity'

const STORAGE_KEY = 'kinotic-clarity-consent'

function readStored(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

function persist(value: 'accepted' | 'declined'): void {
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

  const stored = readStored()
  if (stored === 'accepted') {
    initClarity(projectId)
    setClarityConsent(true)
    return
  }
  if (stored === 'declined') {
    return
  }

  // No prior choice. Visitors outside the EU/UK get analytics loaded
  // without a banner; EU/UK visitors are asked first.
  if (!isEuOrUkVisitor()) {
    initClarity(projectId)
    return
  }

  mountBanner((accepted) => {
    persist(accepted ? 'accepted' : 'declined')
    if (accepted) {
      initClarity(projectId)
      setClarityConsent(true)
    }
  })
})
