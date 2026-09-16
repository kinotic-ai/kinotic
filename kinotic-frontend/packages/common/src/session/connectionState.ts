import { reactive } from 'vue'

/**
 * Whether the client can reach the server right now: false from the moment a connection the client is
 * retrying ends, or the session check cannot get an answer, until it is talking to the server again.
 * Every request an app makes needs the server, so the apps cover themselves while this is false.
 */
export const CONNECTION_STATE = reactive({ reachable: true })
