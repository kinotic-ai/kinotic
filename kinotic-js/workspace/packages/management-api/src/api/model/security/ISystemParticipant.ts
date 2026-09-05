import type {IParticipant} from '@kinotic-ai/core'
import type {ParticipantType} from './ParticipantType'

/**
 * A participant authenticated against the platform SYSTEM scope — a platform operator with no
 * organization or application context. Mirrors the server {@code SystemParticipant}.
 */
export interface ISystemParticipant extends IParticipant {
    type: ParticipantType.SYSTEM;
}
