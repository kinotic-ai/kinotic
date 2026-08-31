import { Kinotic } from '@kinotic-ai/core'
import type { ToastServiceMethods } from 'primevue/toastservice'
import type { Router } from 'vue-router'
import { createDebug } from '../util/debug'
import { showErrorToast } from '../util/helpers'
import type { ISessionState } from './SessionState'

const debug = createDebug('session-loss')

/** Walks the cause chain to the innermost Error, which carries the server's actual message. */
function rootCause(error: Error): Error {
    let ret = error
    while (ret.cause instanceof Error) {
        ret = ret.cause
    }
    return ret
}

/**
 * Installs handling for a realtime connection that dies after login. A fatal event bus error
 * ends the session for good — a STOMP ERROR frame always terminates the connection per
 * protocol — so the handler clears the authenticated state, shows the server's error, and
 * routes to {@code /login?referer=<fullPath>} for the user to sign in again.
 */
export function installSessionLossHandler(router: Router,
                                          sessionState: ISessionState,
                                          toast: ToastServiceMethods): void {
    Kinotic.eventBus.fatalErrors.subscribe((error: Error) => {
        // A fatal before the first login succeeds rejects that login() call, which its caller
        // (the auth guard's session probe, or the login page) already handles. This guard also
        // collapses a burst of fatals into one toast and one redirect.
        if (!sessionState.isAuthenticated()) {
            return
        }
        debug('Fatal event bus error, ending the session: %O', error)
        // Cleared so the auth guard stops admitting protected routes on the dead connection.
        sessionState.connectedInfo = null
        // The outer error is a generic wrapper ('STOMP connection error'); the server's
        // reason — an ERROR frame's message header, or 'Authentication required' — is the root cause.
        showErrorToast(toast, 'Connection lost', rootCause(error))
        const current = router.currentRoute.value
        const { authenticationRequired } = current.meta
        if (authenticationRequired === undefined || authenticationRequired) {
            void router.push({ path: '/login', query: { referer: current.fullPath } })
        }
    })
}
