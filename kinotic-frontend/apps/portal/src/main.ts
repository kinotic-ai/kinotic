import './style.css'
import './theme.css'
import StyleClass from 'primevue/styleclass'
import { createKinoticApp } from '@kinotic-ai/frontend-common'
import router from '@/router'
import { CONTINUUM_UI } from '@/IContinuumUI'
import 'primeicons/primeicons.css'
import App from './App.vue'
import { KinoticStates } from '@/states'

import { Kinotic } from '@kinotic-ai/core'
import { ManagementApiPlugin } from '@kinotic-ai/management-api'
import { PersistencePlugin } from '@kinotic-ai/persistence'

Kinotic.use(ManagementApiPlugin)
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
const app = createKinoticApp({
    root: App,
    router,
    sessionState: KinoticStates.getUserState()
})

CONTINUUM_UI.initialize(router);

app.directive('styleclass', StyleClass)
KinoticStates.getApplicationState().initialize(router)
app.mount('#app')