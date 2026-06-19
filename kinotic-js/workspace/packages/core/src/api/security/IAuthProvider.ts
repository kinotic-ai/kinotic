/**
 * Supplies the headers attached to the WebSocket upgrade request when a Node/Bun
 * client authenticates during the handshake. Implementations are consulted on every
 * (re)connect, so a provider backing short-lived credentials can refresh them each time.
 */
export interface IAuthProvider {
    /**
     * Returns the headers to attach to the WebSocket upgrade request.
     * Invoked on every (re)connect.
     */
    getAuthHeaders(): Promise<Record<string, string>> | Record<string, string>
}
