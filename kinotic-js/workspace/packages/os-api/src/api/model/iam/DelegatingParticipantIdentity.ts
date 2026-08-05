import { ParticipantIdentity } from '@/api/model/iam/ParticipantIdentity'
import { ParticipantIdentityType } from '@/api/model/iam/ParticipantIdentityType'
import { DelegateKind } from '@/api/model/iam/DelegateKind'

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
