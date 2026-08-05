import { ParticipantIdentity } from '@/api/model/iam/ParticipantIdentity'
import { ParticipantIdentityType } from '@/api/model/iam/ParticipantIdentityType'

/**
 * A person. Unique by email within their scope, authenticating with LOCAL credentials or a
 * federated OIDC identity.
 */
export class UserParticipantIdentity extends ParticipantIdentity {
    public readonly type: ParticipantIdentityType.USER = ParticipantIdentityType.USER
    public email: string = ''
    public oidcSubject: string | null = null
    public oidcConfigId: string | null = null
}
