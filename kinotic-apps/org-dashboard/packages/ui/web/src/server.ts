import type { ServerInfo } from '@kinotic-ai/core'

/**
 * The server the dashboard talks to, from the VITE_KINOTIC_HOST / VITE_KINOTIC_PORT /
 * VITE_KINOTIC_USE_SSL variables a deployment hands the build. Empty when VITE_KINOTIC_HOST is
 * unset, so the client resolves the page's own location: the dev server's proxy in development.
 */
export function serverOverrides(): Partial<ServerInfo> {
    const host = import.meta.env.VITE_KINOTIC_HOST
    let ret: Partial<ServerInfo>
    if (host) {
        ret = {
            host,
            port: Number(import.meta.env.VITE_KINOTIC_PORT),
            useSSL: import.meta.env.VITE_KINOTIC_USE_SSL === 'true',
        }
    } else {
        ret = {}
    }
    return ret
}

/**
 * The absolute URL of a kinotic-server REST route on the same server the realtime connection
 * uses, or the path itself when no server override is set, so the dev proxy carries it.
 */
export function apiUrl(path: string): string {
    const { host, port, useSSL } = serverOverrides()
    return host ? `${useSSL ? 'https' : 'http'}://${host}:${port}${path}` : path
}
