import type { MachineParticipantIdentity } from '@/api/model/security/MachineParticipantIdentity'

/**
 * The outcome of provisioning a machine identity: the saved machine and the one and only
 * disclosure of its client secret — only a hash is stored, so the plaintext is unrecoverable
 * once discarded.
 */
export interface MachineProvisionResult {
    machine: MachineParticipantIdentity
    clientSecret: string
}
