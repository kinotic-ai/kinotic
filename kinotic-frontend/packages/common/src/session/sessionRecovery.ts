import { Kinotic } from '@kinotic-ai/core'
import type { Router } from 'vue-router'
import { createDebug } from '../util/debug'
import type { ISessionState } from './SessionState'

const debug = createDebug('session-recovery')

/**
 * Installs recovery for a realtime connection lost after login: re-establishes the connection
 * off the browser session, and when the session itself is no longer valid redirects to
 * {@code /login?referer=<fullPath>} so the user can authenticate again.
 */
export function installSessionRecovery(router: Router,
                                       sessionState: ISessionState): void {
    // Transient network failures are retried inside the event bus and never surface here;
    // fatalErrors emits only once the bus has deactivated for good (the session expired, or
    // the server rejected the connection), after which every request would fail until a new
    // login() reconnects.
    Kinotic.eventBus.fatalErrors.subscribe((error: Error) => {
        // A fatal error before the first login succeeds rejects that login() call, which its
        // caller (the auth guard's session probe, or the login page) already handles.
        if (!sessionState.isAuthenticated()) {
            return
        }
        debug('Fatal event bus error, re-establishing the session: %O', error)
        sessionState.login().catch(() => {
            const current = router.currentRoute.value
            const { authenticationRequired } = current.meta
            if (authenticationRequired === undefined || authenticationRequired) {
                void router.push({ path: '/login', query: { referer: current.fullPath } })
            }
        })
    })
}
