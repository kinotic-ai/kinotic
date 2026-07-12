import type { NavigationGuardNext, RouteLocationNormalized, Router } from 'vue-router'
import type { App, Plugin } from 'vue'
import {KinoticStates} from '@/states/index'

export function createKinoticUI(): Plugin {
    return {
        install(_: App, options: {router: Router, sessionProbe: Promise<void>}) {
            options.router.beforeEach(async (to: RouteLocationNormalized, _: RouteLocationNormalized, next: NavigationGuardNext) => {

                const { authenticationRequired } = to.meta
                if (authenticationRequired === undefined || authenticationRequired) {
                    // Settles once the session-cookie connect attempt finishes, so the guard sees
                    // the real auth state on the initial navigation.
                    await options.sessionProbe
                    if (!KinoticStates.getUserState().isAuthenticated()) {
                        next({ path: '/login', query: { referer: to.fullPath } })
                        return
                    }
                }
                next()
            })

            KinoticStates.getApplicationState().initialize(options.router)
        }
    }
}
