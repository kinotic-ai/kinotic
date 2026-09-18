/// <reference types="vite/client" />

/**
 * The platform address a deployment hands every UI build. Unset in development, where the
 * dev server proxies the page's own origin to kinotic-server.
 */
interface ImportMetaEnv {
    readonly VITE_KINOTIC_HOST?: string
    readonly VITE_KINOTIC_PORT?: string
    readonly VITE_KINOTIC_USE_SSL?: string
}
