import type {CredentialsResolver} from '@/api/security/CredentialsResolver'

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
 * upgrade request (for example, an Authorization header). It may be async —
 * for example, to refresh a short-lived access token before each connect.
 * Browser callers normally leave this unset and rely on the session cookie
 * established by a prior REST login.
 */
export type WebSocketFactory = () => IWebSocket | Promise<IWebSocket>

export class ServerInfo {
    host!: string
    port?: number | null
    useSSL?: boolean | null
}

/**
 * Builds the base URL of a server for the given scheme stem ({@code 'ws'} or {@code 'http'}):
 * TLS appends {@code s}, and the port is omitted when absent so the scheme default applies.
 */
export function buildServerUrl(server: ServerInfo, scheme: 'ws' | 'http'): string {
    return scheme + (server.useSSL ? 's' : '')
        + '://' + server.host
        + (server.port ? ':' + server.port : '')
}

/**
 * Builds the WebSocket broker URL the STOMP client connects to for a given server — the
 * single source of truth for the broker path, so a caller supplying its own
 * {@link WebSocketFactory} never re-types it and drifts from core.
 */
export function buildBrokerUrl(server: ServerInfo): string {
    return buildServerUrl(server, 'ws') + '/v1'
}

/**
 * Copies the server fields of resolved {@link ConnectOptions} into a {@link ServerInfo},
 * leaving credential material behind.
 */
export function toServerInfo(options: ConnectOptions): ServerInfo {
    const serverInfo = new ServerInfo()
    serverInfo.host = options.host as string
    serverInfo.port = options.port
    serverInfo.useSSL = options.useSSL
    return serverInfo
}

export enum SessionKeepAliveMode {
    NONE = 'NONE',
    ACTIVITY = 'ACTIVITY',
    CONNECTION = 'CONNECTION'
}

/**
 * Options for {@link IKinotic#connect}. Every field is optional: absent server fields resolve
 * from the environment (the {@code KINOTIC_SERVER_HOST} / {@code KINOTIC_SERVER_PORT} /
 * {@code KINOTIC_SERVER_USE_SSL} variables, the browser's own location, then
 * {@code https://api.kinotic.com}), and absent {@link credentials} resolve through the
 * default {@link ChainedCredentialsResolver} (environment variables, then the browser
 * session).
 *
 * Authentication is performed during the WebSocket upgrade (handshake), not in the STOMP
 * CONNECT frame: the resolved credentials supply the upgrade headers, or none for
 * session-cookie auth.
 */
export interface ConnectOptions {
    host?: string
    port?: number | null
    useSSL?: boolean | null

    /**
     * Finds the credentials this connection authenticates with, consulted on every
     * (re)connect. Absent, the default resolution chain applies.
     */
    credentials?: CredentialsResolver

    /**
     * Escape hatch: fully replaces socket construction and credential resolution. When set,
     * {@link credentials} is ignored and the factory owns the upgrade request.
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
    sessionKeepAlive?: SessionKeepAliveMode
}
