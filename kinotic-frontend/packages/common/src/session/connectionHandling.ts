import { ConnectedInfo, Kinotic } from '@kinotic-ai/core'
import type { ToastServiceMethods } from 'primevue/toastservice'
import type { Router } from 'vue-router'
import { createDebug } from '../util/debug'
import { apiUrl, showErrorToast } from '../util/helpers'
import { CONNECTION_STATE } from './connectionState'
import type { ISessionState } from './SessionState'

const debug = createDebug('connection')

// how long the session check waits for the server before the end is reported as a lost connection
const SESSION_CHECK_TIMEOUT_MS = 5000

/** Walks the cause chain to the innermost Error, which carries the server's actual message. */
function rootCause(error: Error): Error {
    let ret = error
    while (ret.cause instanceof Error) {
        ret = ret.cause
    }
    return ret
}

/**
 * True when the server answers, within {@link SESSION_CHECK_TIMEOUT_MS}, that the page's session cookie no
 * longer holds a login.
 */
async function isSignedOut(): Promise<boolean> {
    let ret = false
    try {
        const res = await fetch(apiUrl('/api/auth/me'), {
            credentials: 'include',
            signal: AbortSignal.timeout(SESSION_CHECK_TIMEOUT_MS)
        })
        ret = res.status === 401
    } catch (error) {
        debug('Session check failed: %O', error)
    }
    return ret
}

/**
 * Installs the app's handling of the realtime connection every request rides on. A connection the client
 * is retrying covers the app until it is back; one the client will not retry ends the session: the
 * handler clears the authenticated state, tells the user they were signed out (or, when the session is
 * still live, why the connection was lost), and routes to {@code /login?referer=<fullPath>} for the user
 * to sign in again.
 */
export function installConnectionHandler(router: Router,
                                         sessionState: ISessionState,
                                         toast: ToastServiceMethods): void {

    async function endSession(reason: Error): Promise<void> {
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
        const current = router.currentRoute.value
        const { authenticationRequired } = current.meta
        if (authenticationRequired === undefined || authenticationRequired) {
            void router.push({ path: '/login', query: { referer: current.fullPath } })
        }
        // The gateway closes the connection when its login's session expires or is signed out elsewhere;
        // the session check tells that ordinary end of the session apart from a lost connection.
        const signedOut = await isSignedOut()
        // a sign-in made while the server answered started a session of its own, which this end says nothing about
        if (!sessionState.isAuthenticated()) {
            if (signedOut) {
                toast.add({
                    severity: 'info',
                    summary: 'Signed out',
                    detail: 'Your session has ended. Sign in again to continue.',
                    life: 5000
                })
            } else {
                showErrorToast(toast, 'Connection lost', reason)
            }
        }
    }

    Kinotic.eventBus.connectionEnded.subscribe((reason: Error | null) => {
        if (Kinotic.eventBus.isConnectionActive()) {
            // The client is reconnecting on its own, and the session cookie outlives the connection. Only a drop of
            // the authenticated connection counts: a disconnect() from login() or logout() of a connection that
            // already dropped reports no end that would mark the server reachable again.
            if (sessionState.isAuthenticated()) {
                CONNECTION_STATE.reachable = false
            }
        } else {
            // The client stopped retrying on the server's answer or on its own request, so the app is not
            // waiting on the server any more: the toast and the login page are the message rather than the overlay.
            CONNECTION_STATE.reachable = true
            // The outer error is a generic wrapper ('STOMP connection error'); the server's reason — an
            // ERROR frame's message header, or 'Authentication required' — is the root cause.
            void endSession(reason !== null ? rootCause(reason)
                                            : new Error('The connection to the server was closed'))
        }
    })

    // the client retries a failed attempt, during login() as well as while reconnecting, so the app waits on the server
    Kinotic.eventBus.connectionAttemptFailed.subscribe(() => {
        CONNECTION_STATE.reachable = false
    })

    Kinotic.eventBus.connectionEstablished.subscribe((connectedInfo: ConnectedInfo) => {
        CONNECTION_STATE.reachable = true
        // each connection is issued a session view of its own, and the one login() stored ended with its
        // connection, so everything reading the participant follows the live one
        sessionState.connectedInfo = connectedInfo
        const current = router.currentRoute.value
        const referer = current.query.referer
        // The guard parks a navigation it could not answer for on /login, which is where an unreachable
        // server leaves the user. The session it was waiting for is here, so it goes on to what was asked
        // for; the login page's own redirect names the same route, and the second navigation is dropped.
        if (current.meta.authenticationRequired === false && typeof referer === 'string') {
            void router.push(referer)
        }
    })
}
