import PrimeVue from 'primevue/config'
import ConfirmationService from 'primevue/confirmationservice'
import ToastService from 'primevue/toastservice'
import { type App, type Component, createApp } from 'vue'
import type { Router } from 'vue-router'
import { KinoticPreset } from './KinoticPreset'
import { installAuthGuard } from './session/authGuard'
import type { ISessionState } from './session/SessionState'

export interface KinoticAppOptions {
    root: Component
    router: Router
    sessionState: ISessionState
}

/**
 * Creates a Vue app with the Kinotic chrome every client shares: the PrimeVue theme preset,
 * the toast service, the router, and the authentication guard backed by {@code sessionState}.
 * Returns the app unmounted so the caller can add app-specific plugins before {@code mount}.
 */
export function createKinoticApp({ root, router, sessionState }: KinoticAppOptions): App {
    const app = createApp(root)

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

    // Probe for an existing browser session in the background. The auth guard awaits this
    // promise before checking auth state, so protected routes wait for the real result while
    // public routes (login, signup, verify) render immediately instead of blanking until the
    // probe settles.
    const sessionProbe = sessionState.login().catch(() => {})

    installAuthGuard(router, {
        sessionProbe,
        isAuthenticated: () => sessionState.isAuthenticated()
    })

    app.use(ToastService)
    // CrudTable's delete flow uses the confirm service, so every app hosting it needs this
    app.use(ConfirmationService)
    app.use(router)
    return app
}
