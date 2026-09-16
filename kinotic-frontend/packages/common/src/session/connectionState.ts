import { reactive, watch } from 'vue'

/**
 * Whether the client can reach the server right now: false from the moment a connection the client is
 * retrying ends, or the session check cannot get an answer, until it is talking to the server again.
 * Every request an app makes needs the server, so the apps cover themselves while this is false.
 */
export const CONNECTION_STATE = reactive({ reachable: true })

/**
 * Resolves as soon as the client cannot reach the server, and at once when it already cannot. A caller
 * waiting on the session uses it to stop waiting on a server that is not answering.
 */
export function serverUnreachable(): Promise<void> {
    let ret: Promise<void>
    if (CONNECTION_STATE.reachable) {
        ret = new Promise<void>(resolve => {
            const stop = watch(() => CONNECTION_STATE.reachable, (reachable: boolean) => {
                if (!reachable) {
                    stop()
                    resolve()
                }
            })
        })
    } else {
        ret = Promise.resolve()
    }
    return ret
}
