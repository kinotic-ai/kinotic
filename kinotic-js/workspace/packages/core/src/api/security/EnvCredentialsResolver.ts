import type {ServerInfo} from '@/api/ConnectOptions'
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

    public async resolve(_server: ServerInfo): Promise<ResolvedCredentials | null> {
        const env = typeof process !== 'undefined' ? process.env : undefined
        let ret: ResolvedCredentials | null = null
        if (env?.KINOTIC_CLIENT_ID && env?.KINOTIC_CLIENT_SECRET) {
            const authHeaders: Record<string, string> = {
                clientId: env.KINOTIC_CLIENT_ID,
                clientSecret: env.KINOTIC_CLIENT_SECRET
            }
            if (env.KINOTIC_ORGANIZATION_ID) authHeaders.organizationId = env.KINOTIC_ORGANIZATION_ID
            if (env.KINOTIC_APPLICATION_ID) authHeaders.applicationId = env.KINOTIC_APPLICATION_ID
            ret = {
                authHeaders,
                organizationId: env.KINOTIC_ORGANIZATION_ID,
                applicationId: env.KINOTIC_APPLICATION_ID
            }
        } else if (env?.KINOTIC_TOKEN) {
            ret = {authHeaders: {Authorization: `Bearer ${env.KINOTIC_TOKEN}`}}
        }
        return ret
    }
}
