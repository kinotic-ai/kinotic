import type {ServerInfo} from '@/api/ConnectOptions'
import type {CredentialsResolver, ResolvedCredentials} from '@/api/security/CredentialsResolver'

/**
 * The browser's session cookie, established by a prior REST login. Resolves to credentials
 * with no headers — the cookie rides the upgrade request on its own — and only in an
 * environment that has one (a browser); elsewhere it passes to the next resolver.
 */
export class SessionCredentialsResolver implements CredentialsResolver {

    public readonly name: string = 'SessionCredentialsResolver'

    public async resolve(_server: ServerInfo): Promise<ResolvedCredentials | null> {
        // document presence is the browser signal; a Node process has no cookie jar
        return typeof document !== 'undefined' ? {} : null
    }
}
