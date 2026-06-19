import type {ServerInfo} from '@/api/ConnectionInfo'

/**
 * Builds the WebSocket broker URL the STOMP client connects to for a given server.
 *
 * Single source of truth for the broker path: used by {@link StompConnectionManager}
 * and by {@link createAuthenticatedWebSocketFactory}, so a Node/Bun caller that supplies
 * its own {@link WebSocketFactory} never has to re-type the path and drift from core.
 */
export function buildBrokerUrl(serverInfo: ServerInfo): string {
    return 'ws' + (serverInfo.useSSL ? 's' : '')
        + '://' + serverInfo.host
        + (serverInfo.port ? ':' + serverInfo.port : '') + '/v1'
}
