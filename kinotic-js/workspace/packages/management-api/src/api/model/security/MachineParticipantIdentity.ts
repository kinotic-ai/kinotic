import { ParticipantIdentity } from '@/api/model/security/ParticipantIdentity'
import { ParticipantIdentityType } from '@/api/model/security/ParticipantIdentityType'

/**
 * A non-human principal with its own credential — a platform daemon or an external caller of
 * an organization's application API. Connects through the Kinotic client with the identity's
 * id as clientId, verified against the secret issued at provisioning.
 */
export class MachineParticipantIdentity extends ParticipantIdentity {
    public readonly type: ParticipantIdentityType.MACHINE = ParticipantIdentityType.MACHINE
}
