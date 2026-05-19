/**
 * Structural shape of a WebSocket used by the underlying STOMP client.
 * Copied from the WebSocket interface to avoid pulling in the DOM typelib,
 * so this type stays usable in Node environments where `lib: dom` is not set.
 */
export interface IWebSocket {
    url: string
    binaryType?: string
    readyState: number
    onopen: ((ev?: any) => any) | undefined | null
    onclose: ((ev?: any) => any) | undefined | null
    onerror: ((ev: any) => any) | undefined | null
    onmessage: ((ev: any) => any) | undefined | null
    close(code?: number, reason?: string): void
    send(data: string | ArrayBuffer): void
}

/**
 * Factory invoked on every (re)connect to produce the WebSocket the STOMP
 * client will use. Supply this in Node when you need to set headers on the
 * upgrade request (for example, an Authorization header). Browser callers
 * normally leave this unset and rely on the session cookie established by a
 * prior REST login.
 */
export type WebSocketFactory = () => IWebSocket

export class ServerInfo {
    host!: string
    port?: number | null
    useSSL?: boolean | null
}

export enum SessionKeepAliveMode {
    NONE = 'NONE',
    ACTIVITY = 'ACTIVITY',
    CONNECTION = 'CONNECTION'
}

/**
 * ConnectionInfo provides the information needed to connect to the kinoitc server.
 *
 * Authentication is performed during the WebSocket upgrade (handshake), not in
 * the STOMP CONNECT frame. In the browser, log in via the REST endpoints first
 * and the established session cookie will be used. In Node, supply a
 * {@link WebSocketFactory} that attaches the required upgrade headers.
 */
export class ConnectionInfo extends ServerInfo {
    /**
     * Optional factory used to create the underlying WebSocket. Use this in
     * Node to attach custom headers (such as Authorization) to the upgrade
     * request. If omitted, a default WebSocket is created and authentication
     * is expected to come from the session cookie.
     */
    webSocketFactory?: WebSocketFactory

    /**
     * The maximum number of connection attempts to make during the {@link IEventBus} initial connection request.
     * If the limit is reached the {@link IEventBus} will return an error to the caller of {@link IEventBus#connect}
     * Set to 0, undefined, or null to try forever
     */
    maxConnectionAttempts?: number | null

    /**
     * Controls whether session expiration is extended by gateway activity or by an active websocket connection.
     * Defaults to {@link SessionKeepAliveMode.ACTIVITY}.
     * Use {@link SessionKeepAliveMode.NONE} to remove the session when the websocket connection closes.
     */
    sessionKeepAlive?: SessionKeepAliveMode | null

}
