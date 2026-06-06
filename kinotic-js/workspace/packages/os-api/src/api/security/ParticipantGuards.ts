import {ParticipantType} from './ParticipantType'
import type {IParticipant} from '@kinotic-ai/core'
import type {IApplicationParticipant} from './IApplicationParticipant'
import type {IOrganizationParticipant} from './IOrganizationParticipant'
import type {ISystemParticipant} from './ISystemParticipant'

/**
 * Reads the scope discriminator the server sends on every participant. The base RPC
 * {@link IParticipant} does not statically carry it — it is introduced by this OS contract layer —
 * so it is read off the wire object here.
 */
function scopeType(participant: IParticipant): ParticipantType | undefined {
    return (participant as unknown as { type?: ParticipantType }).type
}

/**
 * @return true if the participant authenticated against the platform SYSTEM scope
 */
export function isSystemParticipant(participant: IParticipant): participant is ISystemParticipant {
    return scopeType(participant) === ParticipantType.SYSTEM
}

/**
 * Narrows to an {@link IOrganizationParticipant}, which carries an {@code organizationId}. An
 * application-scoped participant satisfies this too, since every Application is owned by an
 * Organization.
 *
 * @return true if the participant authenticated against an Organization or an Application
 */
export function isOrganizationParticipant(participant: IParticipant): participant is IOrganizationParticipant {
    const type = scopeType(participant)
    return type === ParticipantType.ORGANIZATION || type === ParticipantType.APPLICATION
}

/**
 * @return true if the participant authenticated against an Application
 */
export function isApplicationParticipant(participant: IParticipant): participant is IApplicationParticipant {
    return scopeType(participant) === ParticipantType.APPLICATION
}
