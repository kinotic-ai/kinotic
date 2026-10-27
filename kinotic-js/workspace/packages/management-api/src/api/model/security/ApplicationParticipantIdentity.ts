import { ParticipantIdentityType } from '@/api/model/security/ParticipantIdentityType'
import { UserParticipantIdentity } from '@/api/model/security/UserParticipantIdentity'

/**
 * A user of one application's own user base, signing in at that application's login; unique by email within the
 * application.
 */
export class ApplicationParticipantIdentity extends UserParticipantIdentity {
    public readonly type: ParticipantIdentityType.APPLICATION_USER = ParticipantIdentityType.APPLICATION_USER
    /** The organization that owns the user's application. */
    public organizationId: string = ''
    /** The application whose user this is. */
    public applicationId: string = ''
    /**
     * The tenant of the application this user belongs to: one of its own, which the system gives it, or one the
     * application's administrators manage.
     */
    public tenantId: string | null = null
}
