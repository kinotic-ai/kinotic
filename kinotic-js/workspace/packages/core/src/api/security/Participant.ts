import type {IApplicationParticipant} from './IApplicationParticipant'
import type {IOrganizationParticipant} from './IOrganizationParticipant'
import type {ISystemParticipant} from './ISystemParticipant'

/**
 * A logged-in participant in one of its concrete scope shapes. Narrow with the
 * {@code isSystemParticipant} / {@code isOrganizationParticipant} / {@code isApplicationParticipant}
 * guards (or on the {@code type} discriminator) to access scope-specific fields.
 */
export type Participant = ISystemParticipant | IOrganizationParticipant | IApplicationParticipant
