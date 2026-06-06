import type {Identifiable} from '@/api/crud/Identifiable'
import type {ParticipantType} from './ParticipantType'

/**
 * Identifying information about a logged-in participant.
 *
 * The concrete shape is determined by {@link type}: a participant is one of
 * {@link ISystemParticipant}, {@link IOrganizationParticipant}, or {@link IApplicationParticipant}.
 * Code that needs scope context should narrow on {@link type} — most conveniently through the
 * {@code isSystemParticipant} / {@code isOrganizationParticipant} / {@code isApplicationParticipant}
 * guards — rather than reading scope fields off this base type.
 *
 * Mirrors the server {@code org.kinotic.core.api.security.Participant}.
 */
export interface IParticipant extends Identifiable<string> {

    /**
     * The scope layer this participant authenticated against.
     */
    type: ParticipantType;

    /**
     * The identity of the participant.
     */
    id: string;

    /**
     * Key/value pairs carrying additional information about the participant.
     */
    metadata: Record<string, string>;

    /**
     * Roles used to authorize the participant to perform actions.
     */
    roles: string[];
}
