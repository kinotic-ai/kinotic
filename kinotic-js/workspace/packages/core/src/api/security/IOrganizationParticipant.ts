import type {IParticipant} from './IParticipant'
import type {ParticipantType} from './ParticipantType'

/**
 * A participant authenticated against an Organization. {@link organizationId} is the id of the
 * owning Organization and is never null. {@link IApplicationParticipant} extends this type, so an
 * application-scoped participant also satisfies it. Mirrors the server
 * {@code OrganizationParticipant}.
 */
export interface IOrganizationParticipant extends IParticipant {
    type: ParticipantType.ORGANIZATION | ParticipantType.APPLICATION;

    /**
     * The id of the Organization this participant is authenticated under; never null.
     */
    organizationId: string;
}
