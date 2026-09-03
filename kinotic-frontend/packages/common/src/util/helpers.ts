import type {ServerInfo} from "@kinotic-ai/core";
import type {ToastServiceMethods} from "primevue/toastservice";

/**
 * The gateway overrides the VITE_KINOTIC_HOST/PORT/USE_SSL build vars declare, for both the
 * STOMP connect and REST URLs. With no VITE_KINOTIC_HOST the object is empty and core's own
 * resolution applies — the page's location, so a same-origin deployment (SPA served from
 * kinotic-server's webroot) and vite's dev proxy both work untouched.
 */
export function serverOverrides(): Partial<ServerInfo> {
    const host = import.meta.env.VITE_KINOTIC_HOST
    if (!host) return {}
    const port = import.meta.env.VITE_KINOTIC_PORT ? parseInt(import.meta.env.VITE_KINOTIC_PORT) : 58503
    let useSSL = import.meta.env.VITE_KINOTIC_USE_SSL ? import.meta.env.VITE_KINOTIC_USE_SSL === 'true' : false
    if (!import.meta.env.VITE_KINOTIC_USE_SSL
        && window.location.protocol.startsWith('https')) {
        useSSL = true
    }
    return {host, port, useSSL}
}

/**
 * Builds the absolute URL for a kinotic-server REST endpoint from the same overrides the
 * STOMP connect uses. Returns the path unchanged when no host override is set so vite's dev
 * proxy handles it (and so a same-origin production deployment still works).
 */
export function apiUrl(path: string): string {
    const suffix = path.startsWith('/') ? path : `/${path}`
    const {host, port, useSSL} = serverOverrides()
    if (!host) return suffix
    return `${useSSL ? 'https' : 'http'}://${host}:${port}${suffix}`
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
        detail: err instanceof Error && err.message ? err.message : (opts.fallback ?? 'An unexpected error occurred'),
        life: opts.life ?? 5000
    })
}

/**
 * Up to two uppercase initials standing in for an avatar image, from the display name or,
 * when it is blank, the email's local part. Empty when neither is available.
 */
export function avatarInitials(displayName?: string | null, email?: string | null): string {
    const name = displayName?.trim() ?? ''
    const source = name.length > 0 ? name : (email?.split('@')[0] ?? '')
    return source.split(/[\s._-]+/)
                 .filter(part => part.length > 0)
                 .slice(0, 2)
                 .map(part => part.charAt(0).toUpperCase())
                 .join('')
}

/** A commit sha abbreviated to the 12 characters the console shows for one. */
export function shortSha(sha: string): string {
    return sha.slice(0, 12)
}

/** Renders a megabyte quantity as GB once it reaches one, e.g. 512 MB, 1.5 GB. */
export function formatMb(mb: number): string {
    return mb >= 1024 ? `${(mb / 1024).toFixed(1)} GB` : `${mb} MB`
}
