import { ParticipantIdentity } from '@/api/model/security/ParticipantIdentity'
import { ParticipantIdentityType } from '@/api/model/security/ParticipantIdentityType'

/**
 * A person. An account, with no organizationId or applicationId, is the person's single identity on the
 * platform, unique by email, signing in with a password and any number of provider bindings and acting
 * in the scopes its memberships name. An application's user, with organizationId and applicationId set,
 * belongs to that application's own user base.
 */
export class UserParticipantIdentity extends ParticipantIdentity {
    public readonly type: ParticipantIdentityType.USER = ParticipantIdentityType.USER
    /** Email address, unique among accounts, or within the application for an application's user. */
    public email: string = ''
}
