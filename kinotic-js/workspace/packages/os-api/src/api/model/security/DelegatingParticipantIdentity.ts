import { ParticipantIdentity } from '@/api/model/security/ParticipantIdentity'
import { ParticipantIdentityType } from '@/api/model/security/ParticipantIdentityType'
import { DelegateKind } from '@/api/model/security/DelegateKind'

/**
 * A client (a CLI install, an MCP host such as an LLM) a user has authorized to act on their
 * behalf. Carries the owning user's scope, is unique by (ownerId, clientKey), and can be
 * revoked without touching the owner.
 */
export class DelegatingParticipantIdentity extends ParticipantIdentity {
    public readonly type: ParticipantIdentityType.DELEGATE = ParticipantIdentityType.DELEGATE
    public ownerId: string = ''
    public clientKey: string = ''
    public delegateKind: DelegateKind | null = null
}
