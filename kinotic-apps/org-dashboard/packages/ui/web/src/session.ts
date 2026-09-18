import { Kinotic } from '@kinotic-ai/core'
import { type IOrganizationParticipant, isOrganizationParticipant } from '@kinotic-ai/management-api'
import { reactive } from 'vue'
import { apiUrl, serverOverrides } from './server'

/**
 * The browser's login to Kinotic OS as an organization user. `login` verifies the email and
 * password at the gateway's organization login route, which establishes the session cookie;
 * the realtime connection then opens with no credentials of its own, authenticated by that
 * cookie, and is admitted only when it authenticated an organization-scoped participant.
 * `restore` reopens the connection from the cookie an earlier login left, and `logout` ends
 * both the server session and the connection.
 */
interface SessionState {
    /** The signed-in organization user, or null while nobody is signed in. */
    participant: IOrganizationParticipant | null
    /** Why the last session ended out from under the dashboard, or null. */
    ended: string | null
}

const state = reactive<SessionState>({ participant: null, ended: null })

/** The session as the views read it; only this module writes it. */
export const session: Readonly<SessionState> = state

export async function login(email: string, password: string): Promise<void> {
    // credentials: 'include' stores the Set-Cookie of a server on another origin too
    const response = await fetch(apiUrl('/api/auth/org/login'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ email, password }),
    })
    if (!response.ok) {
        throw new Error(await errorOf(response, 'Invalid credentials'))
    }
    await connect()
}

/**
 * Reopens the connection when a session cookie from an earlier login still authenticates the
 * browser.
 * @return true once connected, false when there is no session to restore
 */
export async function restore(): Promise<boolean> {
    // A rejected WebSocket upgrade is opaque to a browser, so the readable HTTP answer decides
    // whether dialing the socket is worth it
    const response = await fetch(apiUrl('/api/auth/me'), { credentials: 'include' })
    let ret = false
    if (response.ok) {
        await connect()
        ret = true
    }
    return ret
}

export async function logout(): Promise<void> {
    // Cleared before the disconnect so the connectionEnded handler reads it as a teardown this
    // client asked for
    state.participant = null
    await fetch(apiUrl('/api/auth/logout'), { method: 'POST', credentials: 'include' })
    await Kinotic.disconnect()
}

async function connect(): Promise<void> {
    state.ended = null
    // No credentials are passed: the default resolution supplies none in a browser, so the
    // session cookie authenticates the upgrade. The session was just confirmed, so a failing
    // upgrade is a server problem worth reporting rather than retrying forever.
    const connected = await Kinotic.connect({ server: serverOverrides(), maxConnectionAttempts: 3 })
    const participant = connected.participant
    if (!isOrganizationParticipant(participant)) {
        await logout()
        throw new Error('This dashboard admits organization users only')
    }
    state.participant = participant
}

async function errorOf(response: Response, fallback: string): Promise<string> {
    let ret = fallback
    try {
        const body = await response.json()
        if (typeof body?.error === 'string') {
            ret = body.error
        }
    } catch {
        // a body that is not JSON carries no message to show
    }
    return ret
}

// A connection the client is still retrying keeps the session; one it gave up on ends it, and
// the login view shows why
Kinotic.eventBus.connectionEnded.subscribe((reason: Error | null) => {
    if (state.participant !== null && !Kinotic.eventBus.isConnectionActive()) {
        state.participant = null
        state.ended = reason?.message ?? 'The connection to the server was closed'
    }
})
