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
import { createPinia } from 'pinia'
import App from './App.vue'
import { Log } from 'oidc-client-ts'
Log.setLogger(console)

import { Structures } from '@mindignited/structures-api'

// Make Structures globally available for web components
declare global {
  interface Window {
    Structures: typeof Structures
  }
}
window.Structures = Structures
const app = createApp(App)

app.use(createPinia())

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

// Initialize CONTINUUM_UI with the existing router instance
CONTINUUM_UI.initialize(router);

app.directive('styleclass', StyleClass)
app.use(ToastService)
app.use(createStructuresUI(), { router })

app.use(router)

app.mount('#app')