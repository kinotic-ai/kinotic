import type { Identifiable } from '@kinotic-ai/core'
import { AuthType } from '@/api/model/iam/AuthType'

/**
 * Represents an authenticated identity in the IAM system. Scope is encoded structurally by
 * which of {@link organizationId} / {@link applicationId} is set:
 *
 * - both null → SYSTEM
 * - {@link organizationId} only → ORGANIZATION
 * - both set → APPLICATION, with {@link tenantId} identifying the end-user data slice
 */
export class ParticipantIdentity implements Identifiable<string> {
    public id: string | null = null
    public email: string = ''
    public displayName: string | null = null
    public authType: AuthType | null = null
    public oidcSubject: string | null = null
    public oidcConfigId: string | null = null
    public organizationId: string | null = null
    public applicationId: string | null = null
    public tenantId: string | null = null
    public enabled: boolean = true
    public created: number | null = null
    public updated: number | null = null
}
