import { ParticipantIdentity } from '@/api/model/security/ParticipantIdentity'
import { ParticipantIdentityType } from '@/api/model/security/ParticipantIdentityType'

/**
 * A non-human principal with its own credential — a platform daemon, an external caller of an
 * organization's application API, or a process acting for a whole organization such as a
 * project deployment's workloads. Connects through the Kinotic client with the identity's id
 * as clientId, verified against the secret issued at provisioning.
 */
export class MachineParticipantIdentity extends ParticipantIdentity {
    public readonly type: ParticipantIdentityType.MACHINE = ParticipantIdentityType.MACHINE
}
