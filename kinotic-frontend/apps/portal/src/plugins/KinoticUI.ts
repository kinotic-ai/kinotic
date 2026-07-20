import { installAuthGuard } from '@kinotic-ai/frontend-common'
import type { Router } from 'vue-router'
import type { App, Plugin } from 'vue'
import { KinoticStates } from '@/states/index'

export function createKinoticUI(): Plugin {
    return {
        install(_: App, options: { router: Router, sessionProbe: Promise<void> }) {
            installAuthGuard(options.router, {
                sessionProbe: options.sessionProbe,
                isAuthenticated: () => KinoticStates.getUserState().isAuthenticated()
            })

            KinoticStates.getApplicationState().initialize(options.router)
        }
    }
}
