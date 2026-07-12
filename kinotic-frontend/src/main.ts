import { createKinoticUI } from '@/plugins/KinoticUI.js'
import './style.css'
import './theme.css'
import PrimeVue from 'primevue/config'
import StyleClass from 'primevue/styleclass'
import { KinoticPreset } from '@/KinoticPreset'
import router from '@/router'
import ToastService from 'primevue/toastservice'
import ConfirmationService from 'primevue/confirmationservice'
import { CONTINUUM_UI } from '@/IContinuumUI'
import 'primeicons/primeicons.css'
import { createApp } from 'vue'
import App from './App.vue'
import { KinoticStates } from '@/states'

import { Kinotic } from '@kinotic-ai/core'
import { OsApiPlugin } from '@kinotic-ai/os-api'
import { PersistencePlugin } from '@kinotic-ai/persistence'

Kinotic.use(OsApiPlugin)
Kinotic.use(PersistencePlugin)

if (import.meta.env.DEV) {
  try {
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.getRegistrations().then((regs) => {
        regs.forEach((r) => r.unregister())
      })
    }
    if ('caches' in window) {
      caches.keys().then((keys) => keys.forEach((k) => caches.delete(k)))
    }
  } catch {
    // ignore
  }

  window.addEventListener(
    'wheel',
    (e: WheelEvent) => {
      if (Math.abs(e.deltaX) < Math.abs(e.deltaY) || Math.abs(e.deltaX) < 8) return

      let el = e.target as HTMLElement | null
      while (el && el !== document.body) {
        const style = window.getComputedStyle(el)
        const overflowX = style.overflowX
        const canScrollX =
          (overflowX === 'auto' || overflowX === 'scroll') &&
          el.scrollWidth > el.clientWidth + 1

        if (canScrollX) {
          const atLeft = el.scrollLeft <= 0
          const atRight = el.scrollLeft + el.clientWidth >= el.scrollWidth - 1
          if ((atLeft && e.deltaX < 0) || (atRight && e.deltaX > 0)) {
            e.preventDefault()
            e.stopPropagation()
          }
          return
        }
        el = el.parentElement
      }
    },
    { capture: true, passive: false }
  )
}
const app = createApp(App)

app.use(PrimeVue, {
    theme: {
        preset: KinoticPreset,
        options: {
            darkModeSelector: '.dark',
            cssLayer: false,
            prefix: 'p',
        }
    }
})

CONTINUUM_UI.initialize(router);

// Probe for an existing browser session in the background. The auth guard awaits this promise
// before checking auth state, so protected routes wait for the real result while public routes
// (login, signup, verify) render immediately instead of blanking until the probe settles.
const sessionProbe = KinoticStates.getUserState().login().catch(() => {})

app.directive('styleclass', StyleClass)
app.use(ToastService)
app.use(ConfirmationService)
app.use(createKinoticUI(), { router, sessionProbe })
app.use(router)
app.mount('#app')