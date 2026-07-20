import { installAuthGuard, KinoticPreset } from '@kinotic-ai/frontend-common'
import './style.css'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'
import 'primeicons/primeicons.css'
import { createApp } from 'vue'
import { Kinotic } from '@kinotic-ai/core'
import { OsApiPlugin } from '@kinotic-ai/os-api'
import App from './App.vue'
import router from './router'
import { SYSTEM_USER_STATE } from './states/SystemUserState'

Kinotic.use(OsApiPlugin)

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

// Probe for an existing browser session in the background. The auth guard awaits this promise
// before checking auth state, so protected routes wait for the real result while the login
// page renders immediately instead of blanking until the probe settles.
const sessionProbe = SYSTEM_USER_STATE.login().catch(() => {})

installAuthGuard(router, {
    sessionProbe,
    isAuthenticated: () => SYSTEM_USER_STATE.isAuthenticated()
})

app.use(ToastService)
app.use(router)
app.mount('#app')
