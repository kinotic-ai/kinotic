import type { Identifiable } from '@kinotic-ai/core'
import { ParticipantIdentityType } from '@/api/model/security/ParticipantIdentityType'

/**
 * A persisted identity that can authenticate — a person of one scope ({@link UserParticipantIdentity}),
 * a client acting on a person's behalf ({@link DelegatingParticipantIdentity}) or a machine
 * ({@link MachineParticipantIdentity}), discriminated by {@link type}. A system user acts in the SYSTEM scope; an
 * organization's user, an application's user, a delegate and a machine carry the scope they act in, encoded by
 * which of their organizationId / applicationId is set:
 *
 * - both null → SYSTEM
 * - organizationId only → ORGANIZATION
 * - both set → APPLICATION
 */
export abstract class ParticipantIdentity implements Identifiable<string> {
    public id: string | null = null
    public abstract readonly type: ParticipantIdentityType
    public displayName: string | null = null
    public enabled: boolean = true
    public created: string | null = null
    public updated: string | null = null
}
