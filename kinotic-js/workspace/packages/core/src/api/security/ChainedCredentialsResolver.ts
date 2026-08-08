import type {ServerInfo} from '@/api/ConnectOptions'
import type {CredentialsResolver, ResolvedCredentials} from '@/api/security/CredentialsResolver'

/**
 * Consults each resolver in order and returns the first non-null result. The default
 * credential resolution every {@code Kinotic.connect()} without explicit credentials uses is
 * a chain; supply your own to control the sources or their order.
 */
export class ChainedCredentialsResolver implements CredentialsResolver {

    public readonly name: string
    private readonly resolvers: CredentialsResolver[]

    constructor(...resolvers: CredentialsResolver[]) {
        this.resolvers = resolvers
        this.name = `ChainedCredentialsResolver(${resolvers.map(r => r.name).join(' -> ')})`
    }

    public async resolve(server: ServerInfo): Promise<ResolvedCredentials | null> {
        for (const resolver of this.resolvers) {
            const resolved = await resolver.resolve(server)
            if (resolved !== null) {
                return resolved
            }
        }
        return null
    }
}
