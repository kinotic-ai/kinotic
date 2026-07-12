import {ConnectionInfo} from "@kinotic-ai/core";
import type {ToastServiceMethods} from "primevue/toastservice";

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

/**
 * Extracts a human-readable message from a caught error, falling back to the
 * given text when the value is not an Error or carries no message.
 */
function errorMessage(err: unknown, fallback: string): string {
    return err instanceof Error && err.message ? err.message : fallback
}

/**
 * Shows an error toast whose summary names the failed operation and whose detail is the
 * caught error's message. Falls back to {@link opts.fallback} (default "An unexpected error
 * occurred") when the value is not an Error or carries no message.
 *
 * @param toast the PrimeVue toast service from {@code useToast()}
 * @param summary the operation that failed, shown as the toast title
 * @param err the caught error
 * @param opts optional fallback detail and toast lifetime in milliseconds (default 5000)
 */
export function showErrorToast(toast: ToastServiceMethods,
                               summary: string,
                               err: unknown,
                               opts: { fallback?: string; life?: number } = {}): void {
    toast.add({
        severity: 'error',
        summary,
        detail: errorMessage(err, opts.fallback ?? 'An unexpected error occurred'),
        life: opts.life ?? 5000
    })
}
