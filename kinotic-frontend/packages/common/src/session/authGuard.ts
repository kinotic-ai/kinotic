import type { NavigationGuardNext, RouteLocationNormalized, Router } from 'vue-router'

/**
 * Installs the authentication guard: routes whose meta lacks
 * {@code authenticationRequired: false} wait for {@code sessionProbe} to settle, then redirect
 * to {@code /login?referer=<fullPath>} when {@code isAuthenticated()} is false.
 */
export function installAuthGuard(router: Router,
                                 options: { sessionProbe: Promise<void>, isAuthenticated: () => boolean }): void {
    router.beforeEach(async (to: RouteLocationNormalized, _: RouteLocationNormalized, next: NavigationGuardNext) => {
        const { authenticationRequired } = to.meta
        if (authenticationRequired === undefined || authenticationRequired) {
            // Settles once the session-cookie connect attempt finishes, so the guard sees
            // the real auth state on the initial navigation.
            await options.sessionProbe
            if (!options.isAuthenticated()) {
                next({ path: '/login', query: { referer: to.fullPath } })
                return
            }
        }
        next()
    })
}
