import type { Identifiable } from '@kinotic-ai/core'
import { AuthType } from '@/api/model/iam/AuthType'

/**
 * A pending member invitation into an existing organization/application scope. Mirrors the
 * backend {@code PendingInvite}: the invitee accepts via the tokenized link, at which point the
 * record is consumed to create the member.
 */
export class PendingInvite implements Identifiable<string> {
    public id: string | null = null
    public verificationToken: string | null = null
    public expiresAt: number | null = null
    public created: number | null = null
    public email: string = ''
    public displayName: string | null = null
    public authType: AuthType | null = null
    public organizationId: string | null = null
    public applicationId: string | null = null
    public oidcConfigId: string | null = null
    public invitedBy: string | null = null
}
