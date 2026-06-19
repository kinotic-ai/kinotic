import {EventConstants, type IEvent} from '@/api/event/IEventBus'
import {Event} from '@/api/event/EventBus'
import type {ServerInfo} from '@/api/ConnectionInfo'

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
}
