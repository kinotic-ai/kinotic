import { createStructuresUI } from '@/plugins/StructuresUI.js'
import './style.css'
import './theme.css'
import PrimeVue from 'primevue/config'
import StyleClass from 'primevue/styleclass'
import { StructuresPreset } from '@/StructuresPreset'
import router from '@/router'
import ToastService from 'primevue/toastservice'
import { CONTINUUM_UI } from '@/IContinuumUI'
import 'primeicons/primeicons.css'
import { createApp } from 'vue'
import App from './App.vue'
import { Log } from 'oidc-client-ts'
Log.setLogger(console)

import { Structures } from '@kinotic/structures-api'

declare global {
  interface Window {
    Structures: typeof Structures
  }
}
window.Structures = Structures

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
        preset: StructuresPreset,
        options: {
            darkModeSelector: '.structures-admin-dark',
            cssLayer: false,
            prefix: 'p',
        }
    }
})

CONTINUUM_UI.initialize(router);

app.directive('styleclass', StyleClass)
app.use(ToastService)
app.use(createStructuresUI(), { router })

app.use(router)

app.mount('#app')