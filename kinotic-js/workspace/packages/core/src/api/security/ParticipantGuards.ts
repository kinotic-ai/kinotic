import {ParticipantType} from './ParticipantType'
import type {IApplicationParticipant} from './IApplicationParticipant'
import type {IOrganizationParticipant} from './IOrganizationParticipant'
import type {IParticipant} from './IParticipant'
import type {ISystemParticipant} from './ISystemParticipant'

/**
 * @return true if the participant authenticated against the platform SYSTEM scope
 */
export function isSystemParticipant(participant: IParticipant): participant is ISystemParticipant {
    return participant.type === ParticipantType.SYSTEM
}

/**
 * Narrows to an {@link IOrganizationParticipant}, which carries an {@code organizationId}. An
 * application-scoped participant satisfies this too, since every Application is owned by an
 * Organization.
 *
 * @return true if the participant authenticated against an Organization or an Application
 */
export function isOrganizationParticipant(participant: IParticipant): participant is IOrganizationParticipant {
    return participant.type === ParticipantType.ORGANIZATION || participant.type === ParticipantType.APPLICATION
}

/**
 * @return true if the participant authenticated against an Application
 */
export function isApplicationParticipant(participant: IParticipant): participant is IApplicationParticipant {
    return participant.type === ParticipantType.APPLICATION
}
