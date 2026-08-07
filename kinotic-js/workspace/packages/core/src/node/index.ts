import {WebSocket as NodeWebSocket} from 'ws'

/**
 * Installs the `ws` WebSocket as the global WebSocket when the runtime does not provide a
 * header-capable one. Upgrade-header authentication (client credentials, bearer tokens)
 * needs a WebSocket constructor that accepts a `headers` option — `ws` and Bun's built-in
 * do; browsers and some Node versions do not. Call once before {@code Kinotic.connect()}
 * in a Node process.
 */
export function ensureNodeWebSocket(): void {
    if (typeof globalThis.WebSocket === 'undefined') {
        Object.assign(globalThis, {WebSocket: NodeWebSocket})
        return
    }
    // Node's built-in (undici) WebSocket ignores a headers option; ws honors it
    if (typeof process !== 'undefined' && process.versions?.node && !process.versions?.bun) {
        Object.assign(globalThis, {WebSocket: NodeWebSocket})
    }
}
