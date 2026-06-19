import type {IWebSocket, ServerInfo, WebSocketFactory} from '@/api/ConnectionInfo'
import type {IAuthProvider} from '@/api/security/IAuthProvider'
import {buildBrokerUrl} from '@/internal/api/event/BrokerUrl'

/**
 * Builds a {@link WebSocketFactory} that authenticates during the WebSocket upgrade
 * by attaching the headers from {@link IAuthProvider#getAuthHeaders} to the request.
 * The provider is consulted on every (re)connect, so short-lived credentials are
 * refreshed each time. Supply the result as {@link ConnectionInfo#webSocketFactory}.
 *
 * For Node/Bun callers: the returned factory creates the socket with the runtime
 * WebSocket's `headers` option, which the DOM `WebSocket` typings omit and the
 * browser WebSocket does not support. Browser callers continue to omit
 * `webSocketFactory` and authenticate via the session cookie from a prior REST login.
 */
export function createAuthenticatedWebSocketFactory(serverInfo: ServerInfo,
                                                    authProvider: IAuthProvider): WebSocketFactory {
    const brokerUrl: string = buildBrokerUrl(serverInfo)
    return async (): Promise<IWebSocket> => {
        const headers: Record<string, string> = await authProvider.getAuthHeaders()
        // The Node/Bun WebSocket accepts a `headers` option that the DOM lib typings omit.
        const WS = WebSocket as unknown as new (url: string, opts: {headers: Record<string, string>}) => IWebSocket
        return new WS(brokerUrl, {headers})
    }
}
