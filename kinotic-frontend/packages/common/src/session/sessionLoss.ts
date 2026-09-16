import { Kinotic } from '@kinotic-ai/core'
import type { ToastServiceMethods } from 'primevue/toastservice'
import type { Router } from 'vue-router'
import { createDebug } from '../util/debug'
import { showErrorToast } from '../util/helpers'
import type { ISessionState } from './SessionState'

const debug = createDebug('session-loss')

/**
 * How long a dropped connection has to come back before the session ends. The event bus waits its
 * own reconnect delay plus jitter before each attempt, so a shorter window would end sessions a
 * reconnect was about to restore.
 */
const RECONNECT_GRACE_MS = 15000

/** Walks the cause chain to the innermost Error, which carries the server's actual message. */
function rootCause(error: Error): Error {
    let ret = error
    while (ret.cause instanceof Error) {
        ret = ret.cause
    }
    return ret
}

/**
 * Installs handling for a realtime connection that dies after login. Every request the app makes
 * rides that connection, so a connection that does not come back ends the session: the handler
 * clears the authenticated state, shows why, and routes to {@code /login?referer=<fullPath>} for
 * the user to sign in again.
 */
export function installSessionLossHandler(router: Router,
                                          sessionState: ISessionState,
                                          toast: ToastServiceMethods): void {
    let reconnectGrace: ReturnType<typeof setTimeout> | null = null

    function endSession(reason: Error): void {
        // A failure before the first login succeeds rejects that login() call, which its caller
        // (the auth guard's session probe, or the login page) already handles, and login() and
        // logout() clear connectedInfo before they close the connection, so a teardown this client
        // asked for never lands here. This guard also collapses a burst of failures into one toast
        // and one redirect.
        if (!sessionState.isAuthenticated()) {
            return
        }
        debug('Ending the session: %O', reason)
        if (reconnectGrace !== null) {
            clearTimeout(reconnectGrace)
            reconnectGrace = null
        }
        // Cleared so the auth guard stops admitting protected routes on the dead connection.
        sessionState.connectedInfo = null
        showErrorToast(toast, 'Connection lost', reason)
        const current = router.currentRoute.value
        const { authenticationRequired } = current.meta
        if (authenticationRequired === undefined || authenticationRequired) {
            void router.push({ path: '/login', query: { referer: current.fullPath } })
        }
    }

    // A fatal event bus error ends the session for good — a STOMP ERROR frame always terminates the
    // connection per protocol, and the bus makes no further attempt after one.
    Kinotic.eventBus.fatalErrors.subscribe((error: Error) => {
        // The outer error is a generic wrapper ('STOMP connection error'); the server's
        // reason — an ERROR frame's message header, or 'Authentication required' — is the root cause.
        endSession(rootCause(error))
    })

    // A connection that ends without a fatal is retried by the event bus, so the session ends only
    // once the retries have had RECONNECT_GRACE_MS to bring it back. A fatal raised in the meantime,
    // such as the session check rejecting the cookie on the next attempt, ends it sooner and with
    // the server's own reason.
    Kinotic.eventBus.connectionLost.subscribe(() => {
        if (sessionState.isAuthenticated() && reconnectGrace === null) {
            reconnectGrace = setTimeout(() => {
                reconnectGrace = null
                if (!Kinotic.eventBus.isConnected()) {
                    endSession(new Error('The connection to the server could not be re-established'))
                }
            }, RECONNECT_GRACE_MS)
        }
    })
}
