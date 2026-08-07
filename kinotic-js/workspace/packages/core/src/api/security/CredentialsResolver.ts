import type {ServerInfo} from '@/api/ConnectOptions'

/**
 * The outcome of credential resolution: the headers to present on the WebSocket upgrade and
 * the scope the caller belongs to. {@link authHeaders} undefined means the connection
 * authenticates with the browser session cookie instead of headers.
 */
export interface ResolvedCredentials {
    authHeaders?: Record<string, string>
    organizationId?: string
    applicationId?: string
}

/**
 * Finds the credentials a Kinotic connection authenticates with. Consulted on every
 * (re)connect, so an implementation backing short-lived credentials can refresh them each
 * time. Returning null means this resolver found nothing — in a
 * {@link ChainedCredentialsResolver} the next resolver tries; at the top level the connect
 * fails naming every resolver consulted.
 */
export interface CredentialsResolver {

    /** Short name shown in the connect failure that lists the resolvers consulted. */
    readonly name: string

    /**
     * Resolves credentials for a connection to the given server, or null when this resolver
     * has none to offer.
     */
    resolve(server: ServerInfo): Promise<ResolvedCredentials | null>
}
