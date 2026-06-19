import type { IAuthProvider } from '@kinotic-ai/core'

/**
 * {@link IAuthProvider} for Kinotic OS credential authentication. Sends the credential and
 * scope as discrete WebSocket upgrade headers — {@code clientId}, {@code clientSecret}, and,
 * when supplied, {@code organizationId} / {@code applicationId} — which the gateway matches
 * against an {@code IamUser} at handshake time.
 *
 * Scope is structural, determined by which ids are given: neither → SYSTEM, organizationId
 * only → ORGANIZATION, both → APPLICATION.
 */
export class KinoticOsCredentialsAuthProvider implements IAuthProvider {

    private readonly authHeaders: Record<string, string>

    constructor(clientId: string, clientSecret: string, organizationId?: string, applicationId?: string) {
        const authHeaders: Record<string, string> = { clientId, clientSecret }
        if (organizationId != null) authHeaders.organizationId = organizationId
        if (applicationId != null) authHeaders.applicationId = applicationId
        this.authHeaders = authHeaders
    }

    public getAuthHeaders(): Record<string, string> {
        return this.authHeaders
    }
}
