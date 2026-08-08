import type {ServerInfo} from '@/api/ConnectOptions'
import {BasicCredentialsResolver} from '@/api/security/BasicCredentialsResolver'
import {BearerCredentialsResolver} from '@/api/security/BearerCredentialsResolver'
import type {CredentialsResolver, ResolvedCredentials} from '@/api/security/CredentialsResolver'

/**
 * Credentials from environment variables — the zero-code path for machines and services:
 * {@code KINOTIC_CLIENT_ID} + {@code KINOTIC_CLIENT_SECRET} (with optional
 * {@code KINOTIC_ORGANIZATION_ID} / {@code KINOTIC_APPLICATION_ID}) resolve as client
 * credentials, or {@code KINOTIC_TOKEN} as a bearer token. Resolves to null in a browser or
 * when none of the variables are set.
 */
export class EnvCredentialsResolver implements CredentialsResolver {

    public readonly name: string = 'EnvCredentialsResolver'

    public async resolve(server: ServerInfo): Promise<ResolvedCredentials | null> {
        const env = typeof process !== 'undefined' ? process.env : undefined
        let ret: ResolvedCredentials | null = null
        if (env?.KINOTIC_CLIENT_ID && env?.KINOTIC_CLIENT_SECRET) {
            // an empty-string variable counts as unset; || undefined normalizes it away
            ret = await new BasicCredentialsResolver(env.KINOTIC_CLIENT_ID,
                                                     env.KINOTIC_CLIENT_SECRET,
                                                     env.KINOTIC_ORGANIZATION_ID || undefined,
                                                     env.KINOTIC_APPLICATION_ID || undefined).resolve(server)
        } else if (env?.KINOTIC_TOKEN) {
            ret = await new BearerCredentialsResolver(env.KINOTIC_TOKEN).resolve(server)
        }
        return ret
    }
}
