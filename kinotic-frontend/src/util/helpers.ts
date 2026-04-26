import {ConnectionInfo} from "@kinotic-ai/core";

export function createConnectionInfo(): ConnectionInfo {
    // Use build time variable if available, otherwise use default
    const envPort = import.meta.env.VITE_KINOTIC_PORT ? parseInt(import.meta.env.VITE_KINOTIC_PORT) : 58503
    const envHost = import.meta.env.VITE_KINOTIC_HOST ? import.meta.env.VITE_KINOTIC_HOST : 'localhost'
    const envUseSSL = import.meta.env.VITE_KINOTIC_USE_SSL ? import.meta.env.VITE_KINOTIC_USE_SSL === 'true' : false

    return {
        host  : envHost,
        port  : envPort,
        useSSL: envUseSSL,
    }
}

export function apiUrl(path: string): string {
    const { host, port, useSSL } = createConnectionInfo()
    const scheme = useSSL ? 'https' : 'http'
    const suffix = path.startsWith('/') ? path : `/${path}`
    return `${scheme}://${host}:${port}${suffix}`
}