import type {ConnectOptions} from '@/api/ConnectOptions'
import {SessionKeepAliveMode} from '@/api/ConnectOptions'
import {ChainedCredentialsResolver} from '@/api/security/ChainedCredentialsResolver'
import {EnvCredentialsResolver} from '@/api/security/EnvCredentialsResolver'
import {SessionCredentialsResolver} from '@/api/security/SessionCredentialsResolver'

/** The port a local kinotic-server listens on; the last-resort default everywhere. */
const DEFAULT_PORT = 58503

/**
 * Fills every absent {@link ConnectOptions} field from the environment. Server fields
 * resolve explicit value → {@code KINOTIC_SERVER_HOST/PORT/USE_SSL} → the browser's own
 * location (only when the host itself came from the location, so an explicit cross-origin
 * host never inherits the page's port) → {@code localhost:58503}. Absent credentials get the
 * default chain: environment variables, then the browser session.
 */
export function resolveConnectOptions(options?: ConnectOptions): ConnectOptions {
    const opts = options ?? {}
    const env = typeof process !== 'undefined' ? process.env : undefined
    const location = typeof window !== 'undefined' ? window.location : undefined

    let host = opts.host ?? env?.KINOTIC_SERVER_HOST
    const hostFromLocation = host == null && location != null
    if (host == null) {
        host = location?.hostname ?? 'localhost'
    }

    let useSSL = opts.useSSL
    if (useSSL == null) {
        if (env?.KINOTIC_SERVER_USE_SSL != null) {
            useSSL = env.KINOTIC_SERVER_USE_SSL === 'true'
        } else if (hostFromLocation) {
            useSSL = location!.protocol === 'https:'
        } else {
            useSSL = false
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
