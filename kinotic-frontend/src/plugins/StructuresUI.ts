import type { NavigationGuardNext, RouteLocationNormalized, Router } from 'vue-router'
import type { App, Plugin } from 'vue'
import {StructuresStates} from '@/states/index'
import { createDebug } from '@/util/debug'

const debug = createDebug('route-guard')

export function createStructuresUI(): Plugin {
    return {
        install(_: App, options: {router: Router}) {
            options.router.beforeEach((to: RouteLocationNormalized, from: RouteLocationNormalized, next: NavigationGuardNext) => {

                const { authenticationRequired } = to.meta
                const userState = StructuresStates.getUserState()
                const authed = userState.isAuthenticated()
                debug('guard %s -> %s authReq=%s authed=%s connectedInfo=%s',
                    from.fullPath, to.fullPath, authenticationRequired, authed, userState.connectedInfo !== null ? 'set' : 'null')
                if ((authenticationRequired === undefined || authenticationRequired) && !authed){
                    debug('  -> REDIRECT to /login?referer=%s', to.fullPath)
                    console.trace('[route-guard] redirect to /login from', from.fullPath, '->', to.fullPath)
                    next({ path: '/login', query: { referer: to.fullPath } })
                } else {
                    next()
                }
            })

            StructuresStates.getApplicationState().initialize(options.router)
        }
    }
}
