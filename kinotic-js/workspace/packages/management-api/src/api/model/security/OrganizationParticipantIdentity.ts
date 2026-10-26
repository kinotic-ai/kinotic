import { ParticipantIdentityType } from '@/api/model/security/ParticipantIdentityType'
import { UserParticipantIdentity } from '@/api/model/security/UserParticipantIdentity'

/**
 * A member of one organization, signing in at that organization's login; unique by email within the organization. A
 * person in several organizations is a separate user of each, with its own sign-in methods.
 */
export class OrganizationParticipantIdentity extends UserParticipantIdentity {
    public readonly type: ParticipantIdentityType.ORGANIZATION_USER = ParticipantIdentityType.ORGANIZATION_USER
    /** The organization whose member this is. */
    public organizationId: string = ''
}
