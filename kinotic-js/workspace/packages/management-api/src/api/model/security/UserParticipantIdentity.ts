import { ParticipantIdentity } from '@/api/model/security/ParticipantIdentity'
import { ParticipantIdentityType } from '@/api/model/security/ParticipantIdentityType'

/**
 * A person, at any scope: what every kind of user has, an email and sign-in methods of its own. The concrete
 * subtype is the scope the person signs in to — {@link SystemParticipantIdentity},
 * {@link OrganizationParticipantIdentity} or {@link ApplicationParticipantIdentity} — and one email may
 * be a user of each, each with its own sign-in methods.
 */
export abstract class UserParticipantIdentity extends ParticipantIdentity {
    public abstract override readonly type: ParticipantIdentityType.SYSTEM_USER | ParticipantIdentityType.ORGANIZATION_USER | ParticipantIdentityType.APPLICATION_USER
    /** Email address, unique among the users of the same scope. */
    public email: string = ''
    /**
     * Whether the user has yet to sign in for the first time: an administrator added it and no sign-in method signs
     * it in yet, so its invitation, or a first sign-in through one of its scope's providers under its email, is what
     * claims it. Cleared once the user has a password or a bound provider identity.
     */
    public pending: boolean = false
}
