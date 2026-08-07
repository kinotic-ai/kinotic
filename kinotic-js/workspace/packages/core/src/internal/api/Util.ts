import {EventConstants, type IEvent} from '@/api/event/IEventBus'
import {Event} from '@/api/event/EventBus'
import {type ConnectOptions, type ServerInfo, SessionKeepAliveMode} from '@/api/ConnectOptions'
import {ChainedCredentialsResolver} from '@/api/security/ChainedCredentialsResolver'
import {EnvCredentialsResolver} from '@/api/security/EnvCredentialsResolver'
import {SessionCredentialsResolver} from '@/api/security/SessionCredentialsResolver'

/** The port a kinotic-server listens on when running without TLS termination in front. */
const DEFAULT_PORT = 58503

/** Where a fully unconfigured client connects: the Kinotic cloud. */
const DEFAULT_HOST = 'api.kinotic.com'

/**
 * Static utility functions used across the Kinoitc core runtime.
 *
 * @author Navid Mitchell 🤝Grok
 * @since 3/25/2025
 */
export class Util {

    public static createReplyEvent(
        incomingHeaders: Map<string, string>,
        headers?: Map<string, string>,
        body?: Uint8Array
    ): IEvent {
        if (!incomingHeaders) {
            throw new Error("incomingHeaders cannot be null")
        }

        const replyCRI = incomingHeaders.get(EventConstants.REPLY_TO_HEADER)
        if (!replyCRI || replyCRI.trim() === "") {
            throw new Error("No reply-to header found, cannot create outgoing message")
        }

        const newHeaders = new Map<string, string>()
        for (const [key, value] of incomingHeaders) {
            if (key.startsWith("__")) {
                newHeaders.set(key, value)
            }
        }

        if (headers) {
            for (const [key, value] of headers) {
                newHeaders.set(key, value)
            }
        }

        return new Event(replyCRI, newHeaders, body || undefined)
    }

    /**
     * Builds the WebSocket broker URL the STOMP client connects to for a given server.
     *
     * Single source of truth for the broker path: used by {@link StompConnectionManager}
     * and by {@link createAuthenticatedWebSocketFactory}, so a Node/Bun caller that supplies
     * its own {@link WebSocketFactory} never has to re-type the path and drift from core.
     */
    public static buildBrokerUrl(serverInfo: ServerInfo): string {
        return 'ws' + (serverInfo.useSSL ? 's' : '')
            + '://' + serverInfo.host
            + (serverInfo.port ? ':' + serverInfo.port : '') + '/v1'
    }

    /**
     * Fills every absent {@link ConnectOptions} field from the environment. Server fields
     * resolve explicit value → {@code KINOTIC_SERVER_HOST/PORT/USE_SSL} → the browser's own
     * location (only when the host itself came from the location, so an explicit cross-origin
     * host never inherits the page's port) → {@code https://api.kinotic.com}. Absent
     * credentials get the default chain: environment variables, then the browser session.
     */
    public static resolveConnectOptions(options?: ConnectOptions): ConnectOptions {
        const opts = options ?? {}
        const env = typeof process !== 'undefined' ? process.env : undefined
        const location = typeof window !== 'undefined' ? window.location : undefined

        let host = opts.host ?? env?.KINOTIC_SERVER_HOST
        const hostFromLocation = host == null && location != null
        const hostDefaulted = host == null && location == null
        if (host == null) {
            host = location?.hostname ?? DEFAULT_HOST
        }

        let useSSL = opts.useSSL
        if (useSSL == null) {
            if (env?.KINOTIC_SERVER_USE_SSL != null) {
                useSSL = env.KINOTIC_SERVER_USE_SSL === 'true'
            } else if (hostFromLocation) {
                useSSL = location!.protocol === 'https:'
            } else {
                // the cloud endpoint is always TLS; a named host states its own preference
                useSSL = hostDefaulted
            }
        }

        let port = opts.port
        if (port === undefined) {
            if (env?.KINOTIC_SERVER_PORT != null) {
                port = Number(env.KINOTIC_SERVER_PORT)
            } else if (hostFromLocation) {
                // same-origin: an empty location port means the scheme default, so omit it
                port = location!.port ? Number(location!.port) : null
            } else {
                port = useSSL ? null : DEFAULT_PORT
            }
        }

        return {
            ...opts,
            host,
            port,
            useSSL,
            sessionKeepAlive: opts.sessionKeepAlive ?? SessionKeepAliveMode.ACTIVITY,
            credentials: opts.credentials
                ?? new ChainedCredentialsResolver(new EnvCredentialsResolver(), new SessionCredentialsResolver())
        }
    }
}
