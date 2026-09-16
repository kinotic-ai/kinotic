import { ConnectedInfo, Kinotic } from '@kinotic-ai/core'
import type { ToastServiceMethods } from 'primevue/toastservice'
import type { Router } from 'vue-router'
import { createDebug } from '../util/debug'
import { showErrorToast } from '../util/helpers'
import { CONNECTION_STATE } from './connectionState'
import type { ISessionState } from './SessionState'

const debug = createDebug('connection')

/** Walks the cause chain to the innermost Error, which carries the server's actual message. */
function rootCause(error: Error): Error {
    let ret = error
    while (ret.cause instanceof Error) {
        ret = ret.cause
    }
    return ret
}

/**
 * Installs the app's handling of the realtime connection every request rides on. A connection the client
 * is retrying covers the app until it is back; one the client will not retry ends the session: the
 * handler clears the authenticated state, shows why, and routes to {@code /login?referer=<fullPath>} for
 * the user to sign in again.
 */
export function installConnectionHandler(router: Router,
                                         sessionState: ISessionState,
                                         toast: ToastServiceMethods): void {

    function endSession(reason: Error): void {
        // A failure before the first login succeeds rejects that login() call, which its caller (the auth
        // guard's session probe, or the login page) already handles, and login() and logout() clear
        // connectedInfo before they close the connection, so a teardown this client asked for never lands
        // here. This guard also collapses a burst of failures into one toast and one redirect.
        if (!sessionState.isAuthenticated()) {
            return
        }
        debug('Ending the session: %O', reason)
        // Cleared so the auth guard stops admitting protected routes on the dead connection.
        sessionState.connectedInfo = null
        // the server answered, so the toast and the login page are the message rather than the overlay
        CONNECTION_STATE.reachable = true
        showErrorToast(toast, 'Connection lost', reason)
        const current = router.currentRoute.value
        const { authenticationRequired } = current.meta
        if (authenticationRequired === undefined || authenticationRequired) {
            void router.push({ path: '/login', query: { referer: current.fullPath } })
        }
    }

    Kinotic.eventBus.connectionEnded.subscribe((reason: Error | null) => {
        if (!sessionState.isAuthenticated()) {
            return
        }
        if (Kinotic.eventBus.isConnectionActive()) {
            // the client is reconnecting on its own, and the session cookie outlives the connection
            CONNECTION_STATE.reachable = false
        } else {
            // The outer error is a generic wrapper ('STOMP connection error'); the server's reason — an
            // ERROR frame's message header, or 'Authentication required' — is the root cause.
            endSession(reason !== null ? rootCause(reason)
                                       : new Error('The connection to the server was closed'))
        }
    })

    Kinotic.eventBus.connectionEstablished.subscribe((connectedInfo: ConnectedInfo) => {
        CONNECTION_STATE.reachable = true
        // each connection is issued a session view of its own, and the one login() stored ended with its
        // connection, so everything reading the participant follows the live one
        sessionState.connectedInfo = connectedInfo
    })
}
