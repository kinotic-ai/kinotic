import type { MachineType } from '@/api/model/security/MachineType'
import { ParticipantIdentity } from '@/api/model/security/ParticipantIdentity'
import { ParticipantIdentityType } from '@/api/model/security/ParticipantIdentityType'

/**
 * A non-human principal with its own credential — a system daemon, an external caller of an
 * organization's application API, or a process acting for a whole organization such as a
 * project deployment's workloads. Connects through the Kinotic client with the identity's id
 * as clientId, verified against the secret issued at provisioning.
 */
export class MachineParticipantIdentity extends ParticipantIdentity {
    public readonly type: ParticipantIdentityType.MACHINE = ParticipantIdentityType.MACHINE

    /** The organization of the machine's scope, or null for SYSTEM. */
    public organizationId: string | null = null

    /** The application of the machine's scope, or null unless APPLICATION. */
    public applicationId: string | null = null

    /**
     * What the machine is for; set when it is provisioned.
     */
    public machineType: MachineType | null = null
}
