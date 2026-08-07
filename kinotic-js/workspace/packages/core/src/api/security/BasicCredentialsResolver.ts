import type {ServerInfo} from '@/api/ConnectOptions'
import type {CredentialsResolver, ResolvedCredentials} from '@/api/security/CredentialsResolver'

/**
 * Kinotic client credentials: a clientId/clientSecret pair presented as upgrade headers —
 * a user's email and password, or a machine identity's id and provisioned secret. Scope is
 * structural, determined by which ids are given: neither → SYSTEM, organizationId only →
 * ORGANIZATION, both → APPLICATION. For machines the ids are optional; when supplied they
 * must agree with the machine's own scope.
 */
export class BasicCredentialsResolver implements CredentialsResolver {

    public readonly name: string = 'BasicCredentialsResolver'
    private readonly credentials: ResolvedCredentials

    constructor(clientId: string, clientSecret: string, organizationId?: string, applicationId?: string) {
        const authHeaders: Record<string, string> = {clientId, clientSecret}
        if (organizationId != null) authHeaders.organizationId = organizationId
        if (applicationId != null) authHeaders.applicationId = applicationId
        this.credentials = {authHeaders, organizationId, applicationId}
    }

    public async resolve(_server: ServerInfo): Promise<ResolvedCredentials> {
        return this.credentials
    }
}
