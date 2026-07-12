import {ConnectionInfo} from "@kinotic-ai/core";

export function createConnectionInfo(): ConnectionInfo {
    // Use build time variable if available, otherwise use default
    const envPort = import.meta.env.VITE_KINOTIC_PORT ? parseInt(import.meta.env.VITE_KINOTIC_PORT) : 58503
    const envHost = import.meta.env.VITE_KINOTIC_HOST ? import.meta.env.VITE_KINOTIC_HOST : 'localhost'
    let envUseSSL = import.meta.env.VITE_KINOTIC_USE_SSL ? import.meta.env.VITE_KINOTIC_USE_SSL === 'true' : false

    if(!import.meta.env.VITE_KINOTIC_USE_SSL
        && window.location.protocol.startsWith('https')){
        envUseSSL = true
    }

    const connectionInfo = new ConnectionInfo()
    connectionInfo.host = envHost
    connectionInfo.port = envPort
    connectionInfo.useSSL = envUseSSL
    return connectionInfo
}

/**
 * Builds the absolute URL for a kinotic-server REST endpoint, reusing the same
 * VITE_KINOTIC_HOST/PORT/USE_SSL env vars STOMP uses. Returns the path unchanged
 * when VITE_KINOTIC_HOST is unset so vite's dev proxy handles it (and so a
 * same-origin production deployment — SPA served from kinotic-server's webroot —
 * still works).
 */
/**
 * Extracts a human-readable message from a caught error, falling back to the
 * given text when the value is not an Error or carries no message.
 */
export function errorMessage(err: unknown, fallback: string): string {
    return err instanceof Error && err.message ? err.message : fallback
}

export function apiUrl(path: string): string {
    const host = import.meta.env.VITE_KINOTIC_HOST
    const suffix = path.startsWith('/') ? path : `/${path}`
    if (!host) return suffix
    const port = import.meta.env.VITE_KINOTIC_PORT || '58503'
    let useSSL = import.meta.env.VITE_KINOTIC_USE_SSL ? import.meta.env.VITE_KINOTIC_USE_SSL === 'true' : false

    if(!import.meta.env.VITE_KINOTIC_USE_SSL
        && window.location.protocol.startsWith('https')){
        useSSL = true
    }

    const protocol = useSSL ? 'https' : 'http'
    return `${protocol}://${host}:${port}${suffix}`
}
